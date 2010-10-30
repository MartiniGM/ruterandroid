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

/**
 * Trafikanten's api is under trafikanten's control and may not be used 
 * without written permission from trafikanten.no.
 * 
 * See Developer.README
 */

package com.neuron.trafikanten.dataProviders.trafikanten;

import org.apache.http.client.methods.HttpGet;
import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.os.Message;
import android.util.Log;

import com.neuron.trafikanten.HelperFunctions;
import com.neuron.trafikanten.HelperFunctions.StreamWithTime;
import com.neuron.trafikanten.dataProviders.GenericDataProviderThread;
import com.neuron.trafikanten.dataProviders.IGenericProviderHandler;
import com.neuron.trafikanten.dataSets.RealtimeData;

public class TrafikantenRealtime extends GenericDataProviderThread<RealtimeData> {
	public final static int MSG_TIMEDATA = 10;
	private static final String TAG = "Trafikanten-T-RealtimeThread";
	private final Context context;
	
	private final int stationId;
	
	public TrafikantenRealtime(Context context, int stationId, IGenericProviderHandler<RealtimeData> handler) {
		super(handler);
		this.context = context;
		this.stationId = stationId;
		start();
	}
	
    @Override
	public void run() {
		try {
			final String urlString = Trafikanten.API_URL + "/RealTime/GetRealTimeData/" + stationId;
			Log.i(TAG,"Loading realtime data : " + urlString);
			
			final StreamWithTime streamWithTime = HelperFunctions.executeHttpRequest(context, new HttpGet(urlString), true);
			ThreadHandleTimeData(streamWithTime.timeDifference);

			/*
			 * Parse json
			 */
			final JSONArray jsonArray = new JSONArray(HelperFunctions.InputStreamToString(streamWithTime.stream));
			final int arraySize = jsonArray.length();
			for (int i = 0; i < arraySize; i++) {
				final JSONObject json = jsonArray.getJSONObject(i);
				
				RealtimeData realtimeData = new RealtimeData();
				realtimeData.line = json.getString("PublishedLineName");
				realtimeData.destination = json.getString("DestinationName");
				realtimeData.realtime = json.getBoolean("Monitored");
				realtimeData.expectedDeparture = HelperFunctions.jsonToDate(json.getString("ExpectedDepartureTime"));
				realtimeData.departurePlatform = Integer.parseInt(json.getString("DeparturePlatformName"));
				realtimeData.stopVisitNote = json.getString("StopVisitNote");
				
				ThreadHandlePostData(realtimeData);
			}

		} catch(Exception e) {
			if (e.getClass() == InterruptedException.class) {
				ThreadHandlePostExecute(null);
				return;
			}
			ThreadHandlePostExecute(e);
			return;
		}
		ThreadHandlePostExecute(null);
    }
    
    
    
    /*
     * Extra thread function to send time difference
     */
    public void ThreadHandleTimeData(Long data) {
    	Message msg = threadHandler.obtainMessage(MSG_TIMEDATA);
    	msg.obj = data;
    	threadHandler.sendMessage(msg);
    }
}
