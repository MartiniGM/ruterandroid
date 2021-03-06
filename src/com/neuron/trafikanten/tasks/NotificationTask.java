package com.neuron.trafikanten.tasks;

import java.util.ArrayList;
import java.util.Calendar;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TimePicker;

import com.google.android.AnalyticsUtils;
import com.neuron.trafikanten.dataSets.NotificationData;
import com.neuron.trafikanten.dataSets.RealtimeData;
import com.neuron.trafikanten.dataSets.RouteDeviData;
import com.neuron.trafikanten.dataSets.RouteProposal;
import com.neuron.trafikanten.dataSets.StationData;
import com.neuron.trafikanten.notification.NotificationIntent;

public class NotificationTask implements GenericTask {
	//private static final String TAG = "Trafikanten-NotificationTask";
	private static int notificationCode = 0;
    private Activity activity;
    private Dialog dialog;
    
    public void init(Activity activity) 
    {
        this.activity = activity;
        AnalyticsUtils.getInstance(activity).trackPageView("/task/notification");
    }
    
	public NotificationTask(Activity activity, RealtimeData realtimeData, StationData station, String with, long timeDifference) {
		init(activity);
		AnalyticsUtils.getInstance(activity).trackEvent("Notification", "Realtime", null, 0);
		showDialog(realtimeData.expectedDeparture + timeDifference, new NotificationData(station, realtimeData, 0, with));
	}
	
	public NotificationTask(Activity activity, ArrayList<RouteProposal> routeProposalList, int proposalPosition, RouteDeviData deviList, long departure, String with) {
		init(activity);
		AnalyticsUtils.getInstance(activity).trackEvent("Notification", "Route", null, 0);
		showDialog(departure, new NotificationData(routeProposalList, proposalPosition, deviList, departure, 0, with));
	}
	
    private void showDialog(final long departure, final NotificationData notificationData) {
    	dialog = new TimePickerDialog(activity, new OnTimeSetListener() {
			@Override
			public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
				/*
				 * Convert hours/minutes to current date with set presets
				 */
				final Calendar calendar = Calendar.getInstance();
				calendar.setTimeInMillis(departure);
				calendar.add(Calendar.HOUR_OF_DAY, -hourOfDay);
				calendar.add(Calendar.MINUTE, -minute);
				
				/*
				 * Get the data info, construct bundle, and send the data
				 */
				final Bundle bundle = new Bundle();
				notificationData.notifyTime = calendar.getTimeInMillis() ;
				bundle.putParcelable(NotificationData.PARCELABLE, notificationData);
				
	            // Schedule the alarm
				final Intent intent = new Intent(activity, NotificationIntent.class);
				intent.putExtras(bundle);
				
				//Log.i(TAG,"Creating notification at " + HelperFunctions.renderAccurate(notificationData.notifyTime));
				final PendingIntent notificationIntent = PendingIntent.getBroadcast(activity, notificationCode++, intent, PendingIntent.FLAG_ONE_SHOT);
	            AlarmManager alarm = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
	            alarm.set(AlarmManager.RTC_WAKEUP, notificationData.notifyTime, notificationIntent);
			}
    		
    	}, 0, 10, true);
    		
		dialog.show();
    }

	@Override
	public void stop() {
		dialog.dismiss();		
	}
}
