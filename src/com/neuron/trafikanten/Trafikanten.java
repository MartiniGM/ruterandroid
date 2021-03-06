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

package com.neuron.trafikanten;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TabActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;

import com.google.android.AnalyticsUtils;
import com.neuron.trafikanten.dataSets.StationData;
import com.neuron.trafikanten.db.FavoriteDbAdapter;
import com.neuron.trafikanten.db.HistoryDbAdapter;
import com.neuron.trafikanten.tasks.ShowTipsTask;
import com.neuron.trafikanten.views.realtime.FavoritesView;
import com.neuron.trafikanten.views.realtime.RealtimeView;
import com.neuron.trafikanten.views.realtime.SelectRealtimeStationView;
import com.neuron.trafikanten.views.route.SelectRouteView;


public class Trafikanten extends TabActivity {
	private static Activity activity;
	public static final String KEY_SELECTEDTAB = "selectedtab";
	public final static String KEY_MYLOCATION = "myLocation";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		/*
		 * Check too old android version
		 *  - We need to parse Build.Version.SDK because SDK_INT isn't available in early android versions
		 */
		if (Integer.parseInt(Build.VERSION.SDK) <= 3) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.legacyMessage)
			       .setCancelable(false)
			       .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.neuron.trafikantenlegacy"));
			        	   startActivity(intent);
			        	   finish();
			        	   return;
			           }
			       });
			AlertDialog alert = builder.create();
			alert.show();
			alert.setOnDismissListener(new OnDismissListener() {

				@Override
				public void onDismiss(DialogInterface dialog) {
					finish();
					return;
				}
				
			});
			return;
		}
		
        /*
         * Google analytics
         */
		AnalyticsUtils.getInstance(this).trackPageView("/home");
		
		Trafikanten.activity = this;
	 	setTitle(getString(R.string.app_name) + " - " + HelperFunctions.GetApplicationVersion(this));
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        if (isShortcut()) {
        	setVisible(false);
        }
        
        /*
         * Setup tab host
         */
	 	final TabHost tabHost = getTabHost();
	 	{
	 		/*
	 		 * Hack : Tweaks for devices with software keyboards, hide keyboard when switching tabs.
	 		 */
            tabHost.setOnTabChangedListener(new OnTabChangeListener() {
		        public void onTabChanged(String tabId) {
					InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(tabHost.getApplicationWindowToken(), 0);
					
					SharedPreferences preferences = activity.getSharedPreferences("trafikantenandroid", Context.MODE_PRIVATE);
					SharedPreferences.Editor editor = preferences.edit();
					editor.putInt(KEY_SELECTEDTAB, tabHost.getCurrentTab());
					editor.commit();
				}
		    });
	 	}
	 	
	 	{
	 		/*
	 		 * Add realtime tab
	 		 */
	 		final Intent intent = new Intent(this, SelectRealtimeStationView.class);
	 		if (getIntent().hasExtra(KEY_MYLOCATION)) {
	 			intent.putExtra(KEY_MYLOCATION, true);
	 		}
		    tabHost.addTab(tabHost.newTabSpec("RealtimeTab")
		 			.setIndicator(getText(R.string.departures), getResources().getDrawable(R.drawable.ic_menu_recent_history))
		 			.setContent(intent));
	 	}
	     
	 	{
	 		/*
	 		 * Add route tab
	 		 */
		    tabHost.addTab(tabHost.newTabSpec("RouteTab")
		 			.setIndicator(getText(R.string.route), getResources().getDrawable(R.drawable.ic_menu_directions))
		 			.setContent(new Intent(this, SelectRouteView.class)));
	 	}
	 	
	 	{
	 		/*
	 		 * Add favorites tab
	 		 */
		    tabHost.addTab(tabHost.newTabSpec("FavoritesTab")
		 			.setIndicator(getText(R.string.favorites), getResources().getDrawable(R.drawable.ic_list_starred))
		 			.setContent(new Intent(this, FavoritesView.class)));
	 	}
	 	
	 	{
	 		// Sticky tabs, load last used.
			SharedPreferences preferences = activity.getSharedPreferences("trafikantenandroid", Context.MODE_PRIVATE);
	 		tabHost.setCurrentTab(preferences.getInt(KEY_SELECTEDTAB, 0));
	 	}
	 	
	 	new ShowTipsTask(this, Trafikanten.class.getName(), R.string.tipFrontscreen, 35);
	}
	
	private boolean isShortcut() {
		final Intent intent = getIntent();
        final String action = intent.getAction();
        if (!Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
        	return false;        	
        }
        AnalyticsUtils.getInstance(this).trackPageView("/createShortcut");
        
        /*
         * Todo show list of shortcuts to create.
         */
        ArrayList<CharSequence> selectList = new ArrayList<CharSequence>();
        selectList.add(getText(R.string.myLocation));
        
        final ArrayList<StationData> realtimeStations = new ArrayList<StationData>(); 
        
        /*
         * Add all history/favorite stations
         */
        {
        	final FavoriteDbAdapter favoriteDbAdapter = new FavoriteDbAdapter(this);
        	final HistoryDbAdapter historyDbAdapter = new HistoryDbAdapter(this);
        	
        	favoriteDbAdapter.addFavoritesToList(false, realtimeStations);
        	historyDbAdapter.addHistoryToList(false, realtimeStations);
            favoriteDbAdapter.close();
            historyDbAdapter.close();
        }
        for(StationData station : realtimeStations) {
        	selectList.add(station.stopName);
        }
        
        /*
         * Setup select contact alert dialog
         */
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.selectStation);
        final String[] items = new String[selectList.size()];
        selectList.toArray(items);
        
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
            	switch(item) {
            	case 0:
            		// My location
            		createShortcutMyLocation();
            		break;
            	default:
            		// A station
            		final StationData station = realtimeStations.get(item - 1);
            		createShortcutStation(station);
            		break;
            		
            	}
                dialog.dismiss();
                finish();
            }
        });
        
        Dialog dialog = builder.create();
        dialog.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				finish();				
			}
        });
        dialog.show();
        
        return true;
	}
	
	private void createShortcutMyLocation() {
        final Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
        shortcutIntent.setClassName(this, this.getClass().getName());
        shortcutIntent.putExtra(KEY_MYLOCATION, true);
        shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP); 
        /*
         * Setup container
         */
        final Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getText(R.string.myLocation));
        final Parcelable iconResource = Intent.ShortcutIconResource.fromContext(this,  R.drawable.icon);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
        setResult(RESULT_OK, intent);
	}
	
	private void createShortcutStation(StationData station) {
        final Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
        shortcutIntent.setClassName(this, RealtimeView.class.getName());
        shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        final Bundle bundle = new Bundle();
        station.writeSimpleBundle(bundle);
        shortcutIntent.putExtras(bundle);
        
        /*
         * Setup container
         */
        final Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, station.stopName);
        final Parcelable iconResource = Intent.ShortcutIconResource.fromContext(this,  R.drawable.icon);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
        setResult(RESULT_OK, intent);
	}
	
	/*
	 * Function is always used from one of the tab views, therefor this is perfectly fine.
	 */
	public static void tabHostSetProgressBarIndeterminateVisibility(boolean value)
	{
		activity.setProgressBarIndeterminateVisibility(value);
	}
}
