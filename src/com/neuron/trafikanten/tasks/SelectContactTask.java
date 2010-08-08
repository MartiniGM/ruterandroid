/**
 *     Copyright (C) 2009 Anders Aagaard <aagaande@gmail.com>
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.neuron.trafikanten.tasks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.provider.Contacts;
import android.util.Log;
import android.widget.Toast;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import com.neuron.trafikanten.R;
import com.neuron.trafikanten.tasks.handlers.ReturnCoordinatesHandler;

/*
 * This task takes no input, and outputs x,y coordinates for the contact.
 */

public class SelectContactTask implements GenericTask {
    private static final String TAG = "Trafikanten-SelectContactTask";
    private Activity activity;
    private GoogleAnalyticsTracker tracker;
    ReturnCoordinatesHandler handler;
    private Dialog dialog; 
    
    public SelectContactTask(Activity activity, GoogleAnalyticsTracker tracker, ReturnCoordinatesHandler handler) 
    {
        this.activity = activity;
        this.tracker = tracker;
        this.handler = handler;
        tracker.trackPageView("/task/searchContact");
        showDialog();
    }
    
    /*
     * Setup dialog for selecting contact
     */
    private void showDialog() {
        /*
         * Setup list of names with addresses
         */
        ArrayList<String> nameList = new ArrayList<String>();
        final Cursor cursor = activity.managedQuery(Contacts.ContactMethods.CONTENT_URI, CONTACTMETHODS_PROJECTION, FILTER_POSTAL, null, null);
        while (cursor.moveToNext()) {
                nameList.add(cursor.getString(0));
        }
        cursor.close();
        
        /*
         * Check if we found anything at all
         */
        if (nameList.size() == 0) {
        	final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        	builder.setPositiveButton("Back",null);
	        builder.setMessage("No contacts with\n addresses found!");
	        dialog = builder.create();
        	dialog.show();
        	
	        tracker.stop();
	        handler.onCanceled();
	        return;
        }
        
        /*
         * Setup select contact alert dialog
         */
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.selectContact);
        final String[] items = new String[nameList.size()];
        nameList.toArray(items);
        
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                    final Cursor cursor = activity.managedQuery(Contacts.ContactMethods.CONTENT_URI, CONTACTMETHODS_PROJECTION,
                                    Contacts.People.NAME + " == ? AND " +
                                    FILTER_POSTAL, new String[] {items[item]}, null);
                    if (!cursor.moveToNext()) {
                            Log.w(TAG, "Couldn't lookup address for contact after contact selection : " + items[item]);
                            Toast.makeText(activity, "Failed to lookup contact, this is a bug, report!", Toast.LENGTH_SHORT).show();
                            return;
                    }
                    dialog.dismiss();
                    handler.OnStartWork();
                    geoMap(cursor.getString(0), cursor.getString(1));
            }
        });
        
        dialog = builder.create();
		/*
		 * Handler onCancel
		 */
		dialog.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				handler.onCanceled();				
			}
		});
        
        /*
         * Show dialog
         */
        dialog.show();
    }
    
    private static final String FILTER_POSTAL = Contacts.ContactMethods.KIND + "=" + Contacts.KIND_POSTAL;
    private static final String[] CONTACTMETHODS_PROJECTION = new String[] {
            Contacts.People.NAME,
            Contacts.ContactMethods.DATA
    };
    
    /*
     * Do geo mapping.
     */
    private void geoMap(String name, String address) {
        final Geocoder geocoder = new Geocoder(activity);
        try {
            List<Address> addresses = geocoder.getFromLocationName(address, 2, 57, 3, 66, 16);

            switch(addresses.size()) {
            case 0:
                    Toast.makeText(activity, R.string.failedToFindAddress, Toast.LENGTH_SHORT).show();
                    break;
            case 2:
                    Toast.makeText(activity, R.string.multipleAddressesFound, Toast.LENGTH_SHORT).show();
                    // Fallthrough
            default:
                    foundLocation(addresses.get(0));
            }
        } catch (IOException e) {
            /*
             * Pass exceptions to parent
             */
            handler.onError(e);
        }
    }
    
    private void foundLocation(Address location) {
    	tracker.stop();
        handler.onFinished(location.getLatitude(), location.getLongitude());
    }

	@Override
	public void stop() {
		handler.onCanceled();
		dialog.dismiss();
	}
}