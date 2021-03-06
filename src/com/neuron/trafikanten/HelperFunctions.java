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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Typeface;
import android.os.Build;
import android.util.Log;

/*
 * Small helper functions used by multiple classes.
 */
public class HelperFunctions {
	private final static String TAG = "Trafikanten-HelperFunctions";
	public final static SimpleDateFormat hourFormater = new SimpleDateFormat("HH:mm");
	public static final String KEY_DOWNLOADBYTE = "downloadkb";
	public static final int SECOND = 1000;
	public static final int MINUTE = 60 * SECOND;
	public static final int HOUR = 60 * MINUTE;

	/*
	 * Render time in the way trafikanten.no wants
	 *   From 1-9 minutes we use "X m", above that we use HH:MM
	 */
	private static CharSequence nowText;
    public static void renderTime(final StringBuffer txt, Long currentTime, Context context, long time) {
		int diffMinutes = Math.round(((float)(time - currentTime)) / MINUTE);

		if (diffMinutes < -1) {
			// Negative time!
			diffMinutes = diffMinutes * -1;
			txt.append("-");
			txt.append(diffMinutes);
			txt.append(" min");
			return;
		} else if (diffMinutes < 1) {
			if (nowText == null) {
				nowText = context.getText(R.string.now);
			}
			txt.append(nowText);
			return;
		} else if (diffMinutes <= 9) {
			txt.append(diffMinutes);
			txt.append(" min");
			return;
		}
		
		//TODO Performance : hourFormater slow? can we construct HH:mm faster ourselfes and write directly to stringbuffer? aprox 110ms here
		txt.append(hourFormater.format(time));
    }
    
    private static String _applicationVersion = null;
    public static String GetApplicationVersion(Context context) {
    	if (_applicationVersion == null) {
	    	try {
	    		_applicationVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0 ).versionName;
			} catch (NameNotFoundException e) {
				e.printStackTrace();
				_applicationVersion = "0";
			}
    	}
    	return _applicationVersion;
    }
    
	private static String userAgentString = null;
	private static String getUserAgent(Context context) {
		if (userAgentString == null) {
			CharSequence appVersion = GetApplicationVersion(context);

			userAgentString = "TrafikantenAndroid/" + appVersion + " (aagaande) Device/" + 
				Build.VERSION.RELEASE + " (" + Locale.getDefault() + "; " + Build.MODEL + ")";			
			
			// + Locale.getDefault() + ")";
		}
		return userAgentString;
	}
	
	
	/*
	 * Do proper encoding that doesn't add + to the string (with JSON doesn't want)
	 */
	public static String properEncode(String query) throws UnsupportedEncodingException {
		return URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20");
	}
	
	/*
	 * Convert trafikanten's json date to an actual date
	 */
	public static Long jsonToDate(String jsonString) {
		Calendar calendar = Calendar.getInstance();
		int indexOfPlus = jsonString.indexOf("+");
		int startOfDate = 5;
		if (jsonString.startsWith("/")) {
			// LEGACY : parse /Date( strings : Some date strings are /Date(253402297140000+0100)/, new ones are Date(253402297140000+0100)
			startOfDate = 6;
			calendar.setTimeInMillis(Long.parseLong(jsonString.substring(startOfDate, indexOfPlus > 0 ? indexOfPlus : jsonString.length()-2)));
		} else {
			calendar.setTimeInMillis(Long.parseLong(jsonString.substring(startOfDate, indexOfPlus > 0 ? indexOfPlus : jsonString.length()-1)));
		}
		if (indexOfPlus > -1) {
			calendar.setTimeZone(TimeZone.getTimeZone("GMT-"+jsonString.substring(indexOfPlus, indexOfPlus+3)+":00"));
		}
		return calendar.getTimeInMillis();
	}

	
	/*
	 * Converts inputstream to string
	 */
    public static String InputStreamToString(InputStream stream) throws IOException {
    	//long perfSTART = System.currentTimeMillis();
    	
	    final InputStreamReader input = new InputStreamReader(stream, "UTF-8");
	    final char[] buffer = new char[8192];
	    final StringBuilder output = new StringBuilder(8192);
	    try {
	        for(int read = input.read(buffer, 0, buffer.length); read != -1; read = input.read(buffer, 0, buffer.length)) {
	            output.append(buffer, 0, read);
	        }
	    } catch (IOException e) { }
	    //Log.i(TAG,"PERF : Downloading web request took " + ((System.currentTimeMillis() - perfSTART)) + "ms");
	    return output.toString();
    }
	
	/*
	 * Updates statistics for byte downloaded.
	 */
	private static void updateStatistics(Context context, long size) {
		if (size == 0) return;
		final SharedPreferences preferences = context.getSharedPreferences("trafikantenandroid", Context.MODE_PRIVATE);
		Long downloadByte = preferences.getLong(KEY_DOWNLOADBYTE, 0) + size;
		
		final SharedPreferences.Editor editor = preferences.edit();
    	editor.putLong(KEY_DOWNLOADBYTE, downloadByte);
		editor.commit();
	}
	
	/*
	 * Decides on whether or not we should use gzip compression or not.
	 */
	public static class StreamWithTime {
		public InputStream stream;
		public long timeDifference;
		public StreamWithTime(InputStream stream, long timeDifference) {
			this.stream = stream;
			this.timeDifference = timeDifference;
		}
	}
	
	public static StreamWithTime executeHttpRequest(Context context, HttpUriRequest request, boolean parseTime) throws IOException {
		//long perfSTART = System.currentTimeMillis();
		
		/*
		 * Add gzip header
		 */
		request.setHeader("Accept-Encoding", "gzip");
		request.setHeader("User-Agent",getUserAgent(context));
		
		/*
		 * Get the response, if we use gzip use the GZIPInputStream
		 */
		long responseTime = System.currentTimeMillis();
		HttpResponse response = new DefaultHttpClient().execute(request);
		responseTime = (System.currentTimeMillis() - responseTime) / 2;
		InputStream content = response.getEntity().getContent();
		
		/*
		 * Get trafikanten time to calculate time difference
		 */
		long timeDifference = 0;
		if (parseTime) {
			try {
				final String header = response.getHeaders("Date")[0].getValue();
				final long trafikantenTime = new Date(header).getTime();
				//timeDifference = System.currentTimeMillis() - (System.currentTimeMillis() - timeDifference) - trafikantenTime;
				timeDifference = System.currentTimeMillis() - responseTime - trafikantenTime;
				Log.i(TAG,"Timedifference between local clock and trafikanten server : " + timeDifference + "ms (" + (timeDifference / 1000) + "s)");
			} catch (Exception e) {
				e.printStackTrace(); // ignore this error, it's not critical.
			}
		}
		
		Header contentEncoding = response.getFirstHeader("Content-Encoding");
		Header contentLength = response.getFirstHeader("Content-Length");
		if (contentLength != null) {
			updateStatistics(context, Long.parseLong(contentLength.getValue()));
		} else {
			Log.e(TAG,"Contentlength is invalid!");
		}
		
		if (contentEncoding != null && contentEncoding.getValue().equalsIgnoreCase("gzip")) {
			content = new GZIPInputStream(content);
			//Log.i(TAG,"Recieved compressed data - OK");
		} else {
			Log.i(TAG,"Recieved UNCOMPRESSED data - Problem server side");
		}
		
		//Log.i(TAG,"PERF : Sending web request took " + ((System.currentTimeMillis() - perfSTART)) + "ms");
		
		//return content;
		return new StreamWithTime(content, timeDifference);
	}
	
	/*
	 * Create custom font for devi.
	 */
    private static Typeface mTypeface = null;
    public static Typeface getTypeface(Activity activity) {
    	if (mTypeface == null) {
    		mTypeface = Typeface.createFromAsset(activity.getAssets(), "fonts/DejaVuSans.ttf");
    	}
    	return mTypeface;
    }
}
