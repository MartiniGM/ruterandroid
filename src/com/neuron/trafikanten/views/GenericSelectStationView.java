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

package com.neuron.trafikanten.views;

import java.util.ArrayList;

import android.app.ListActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.neuron.trafikanten.R;
import com.neuron.trafikanten.Trafikanten;
import com.neuron.trafikanten.dataProviders.DataProviderFactory;
import com.neuron.trafikanten.dataProviders.ISearchProvider;
import com.neuron.trafikanten.dataProviders.ISearchProvider.SearchProviderHandler;
import com.neuron.trafikanten.dataSets.StationData;
import com.neuron.trafikanten.db.FavoriteDbAdapter;
import com.neuron.trafikanten.db.HistoryDbAdapter;
import com.neuron.trafikanten.tasks.GenericTask;
import com.neuron.trafikanten.tasks.LocationTask;
import com.neuron.trafikanten.tasks.SearchAddressTask;
import com.neuron.trafikanten.tasks.SelectContactTask;
import com.neuron.trafikanten.tasks.handlers.ReturnCoordinatesHandler;
import com.neuron.trafikanten.views.map.GenericMap;

public abstract class GenericSelectStationView extends ListActivity {
	private static final String TAG = "Trafikanten-SelectStationView";
	
	private final static int ADDFAVORITE_ID = Menu.FIRST;
	private final static int REMOVEFAVORITE_ID = Menu.FIRST + 1;
	private final static int REMOVEHISTORY_ID = Menu.FIRST + 2;
	
	/*
	 * Options menu items:
	 */
	private final static int MYLOCATION_ID = Menu.FIRST;
	private final static int MAP_ID = Menu.FIRST + 1;
	private final static int CONTACT_ID = Menu.FIRST + 2;
	private final static int ADDRESS_ID = Menu.FIRST + 3;
	private final static int RESET_ID = Menu.FIRST + 4;
	private final static int HELP_ID = Menu.FIRST + 5;
	
	/*
	 * Database adapter
	 */
	public FavoriteDbAdapter favoriteDbAdapter;
	public HistoryDbAdapter historyDbAdapter;
	
	/*
	 * Views 
	 */
	private TextView infoText;
	
	/*
	 * Task tracking
	 */
	private ISearchProvider searchProvider;
	private GenericTask activeTask;
	
	/*
	 * Saved instance state: (list)
	 */
	public StationListAdapter stationListAdapter;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        /*
         * Setup view
         */
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.selectstation);
		registerForContextMenu(getListView());
		infoText = (TextView) findViewById(R.id.infoText);
        
		/*
		 * Setup adapters, add favorites to list and refresh.
		 */
        favoriteDbAdapter = new FavoriteDbAdapter(this);
        historyDbAdapter = new HistoryDbAdapter(this);
        stationListAdapter = new StationListAdapter(this);
        
        if (savedInstanceState == null) {
        	favoriteDbAdapter.addFavoritesToList(stationListAdapter.getList());
        	historyDbAdapter.addHistoryToList(stationListAdapter.getList());
        } else {
        	final ArrayList<StationData> list = savedInstanceState.getParcelableArrayList(StationListAdapter.KEY_SEARCHSTATIONLIST);
        	stationListAdapter.setList(list);
        }
		refresh();
        
        /*
         * Setup the search editbox to search on Enter.
         */
		final EditText searchEdit = (EditText) findViewById(R.id.search);
		searchEdit.setOnKeyListener(new OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                	/*
                	 * On Search show dialog, clear current list and initiate search thread.
                	 */
                	
                	if (searchEdit.getText().toString().length() == 0) {
                		resetView();
                	} else {
                		searchProvider.Search(searchEdit.getText().toString());
                    	searchEdit.setText("");
                	}
                	return true;
                }
				return false;
			}
		});
		createSearchProvider();
		setListAdapter(stationListAdapter);
    }
    
    /*
     * Setup a stored search provider, this is shared between multiple functions
     */
    private void createSearchProvider() {
       	searchProvider = DataProviderFactory.getSearchProvider(getResources(), new SearchProviderHandler() {
    		@Override
    		public void onData(StationData station) {
    			stationListAdapter.addItem(station);
    			stationListAdapter.notifyDataSetChanged();
    		}

    		@Override
    		public void onError(Exception exception) {
    			Log.w(TAG,"onException " + exception);
    			Toast.makeText(GenericSelectStationView.this, "" + getText(R.string.exception) + "\n" + exception, Toast.LENGTH_LONG).show();
    			Trafikanten.tabHostSetProgressBarIndeterminateVisibility(false);
    		}

    		@Override
    		public void onFinished() {
    			setProgressBar(false);
    			refresh();
    		}

			@Override
			public void onStarted() {
				Log.i(TAG,"searchProvider started");
				stationListAdapter.getList().clear();
				stationListAdapter.notifyDataSetChanged();
				setProgressBar(true);
			}
    	});
    }
    
    public void setAdapterLayout(int layout) {
    	stationListAdapter.setLayout(layout);
    }
    
    /*
     * Create context menu, context menu = long push on list item.
     * @see android.app.Activity#onCreateContextMenu(android.view.ContextMenu, android.view.View, android.view.ContextMenu.ContextMenuInfo)
     */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		
		final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		final StationData stationData = stationListAdapter.getList().get(info.position);
		
		if (stationData.isFavorite) {
			menu.add(0, REMOVEFAVORITE_ID, 0, R.string.removeFavorite);			
		} else {
			menu.add(0, ADDFAVORITE_ID, 0, R.string.addFavorite);
			if (historyDbAdapter.hasStation(stationData.stationId)) {
				menu.add(0, REMOVEHISTORY_ID, 0, R.string.removeHistory);
			}
		}
	}
	
	/*
	 * Select context menu item.
	 * @see android.app.Activity#onContextItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		/*
		 * Get selected item.
		 */
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        final StationData station = (StationData) stationListAdapter.getItem(info.position);

		switch(item.getItemId()) {
		case ADDFAVORITE_ID:
			// Fallthrough, we toggle anyway
	    case REMOVEFAVORITE_ID:
	    	/*
	    	 * Toggle favorite.
	    	 */
	    	if (favoriteDbAdapter.toggleFavorite(station)) {
	    		/*
	    		 * If we add it to favorite, delete it from history.
	    		 */
	    		historyDbAdapter.delete(station.stationId);
	    	}
	    	refresh();
	    	return true;
	    case REMOVEHISTORY_ID:
	    	/*
	    	 * Remove a station from history
	    	 */
	    	historyDbAdapter.delete(station.stationId);
	    	resetView(); // Need full resetView here, as a refresh only refreshes favorites
	    	return true;
	    }
		return super.onContextItemSelected(item);
	}


	/*
	 * Options menu, visible on menu button.
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		final MenuItem myLocation = menu.add(0, MYLOCATION_ID, 0, R.string.myLocation);
		myLocation.setIcon(android.R.drawable.ic_menu_mylocation);
		
		final MenuItem map = menu.add(0, MAP_ID, 0, R.string.map);
		map.setIcon(android.R.drawable.ic_menu_mapmode);
		
		final MenuItem contact = menu.add(0, CONTACT_ID, 0, R.string.contact);
		contact.setIcon(R.drawable.ic_menu_cc);
		
		final MenuItem address = menu.add(0, ADDRESS_ID, 0, R.string.address);
		address.setIcon(R.drawable.ic_menu_directions);
		
		final MenuItem favorites = menu.add(0, RESET_ID, 0, R.string.reset);
		favorites.setIcon(android.R.drawable.ic_menu_revert);
		
		final MenuItem help = menu.add(0, HELP_ID, 0, R.string.help);
		help.setIcon(android.R.drawable.ic_menu_help);
		
		return true;
	}
	
	/*
	 * Options menu item selected, options menu visible on menu button.
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case MYLOCATION_ID:
        	findMyLocationTask();
        	break;
        case MAP_ID:
        	GenericMap.Show(this, stationListAdapter.getList(), 0);
        	break;
        case CONTACT_ID:
        	selectContact();
        	break;
        case ADDRESS_ID:
        	searchAddressTask();
        	break;
        case RESET_ID:
        	resetView();
        	break;
        case HELP_ID:
        	final Intent intent = new Intent("android.intent.action.VIEW", Uri.parse("http://code.google.com/p/trafikanten/"));
        	startActivity(intent);
        	break;
        default:
        	Log.e(TAG, "onOptionsItemSelected unknown id " + item.getItemId());
        }
		return super.onOptionsItemSelected(item);
	}
	
	
	/*
	 * Get a generic handler that returns coordinates
	 */
	private ReturnCoordinatesHandler getReturnCoordinatesHandler() {
		return new ReturnCoordinatesHandler() {
	        @Override
	        public void onCanceled() {
	        	setProgressBar(false);
	            activeTask = null;
	        }
	
	        @Override
	        public void onError(Exception exception) {
	                Log.w(TAG,"onException " + exception);
	                Toast.makeText(GenericSelectStationView.this, "" + getText(R.string.exception) + "\n" + exception, Toast.LENGTH_LONG).show();
	                setProgressBar(false);
	        }
	
	        @Override
	        public void onFinished(double latitude, double longitude) {
	                Log.i(TAG,"selectContactTask finished");
	                setProgressBar(false);
	                activeTask = null;
	                searchProvider.Search(latitude,longitude);
	        }
	
	        @Override
	        public void OnStartWork() {
	                setProgressBar(true);                            
	        }
		};
	}
	
	/*
	 * Deal with a location search
	 */
	private void findMyLocationTask() {
		searchProvider.Stop();
		activeTask = new LocationTask(this, getReturnCoordinatesHandler());
	}
	
	/*
	 * Deal with address search
	 */
	private void searchAddressTask() {
		searchProvider.Stop();
		activeTask = new SearchAddressTask(this, getReturnCoordinatesHandler());
	}
	
	/*
	 * Deal with contact selection
	 */
    private void selectContact() {
        searchProvider.Stop();
        
        activeTask = new SelectContactTask(this, getReturnCoordinatesHandler());
}

	/*
	 * Reset is used by RESET_ID and Settings refresh.
	 */
	private void resetView() {
    	/*
    	 * Reset database connection incase dataprovider has changed
    	 */
    	favoriteDbAdapter.close();
    	historyDbAdapter.close();
        favoriteDbAdapter = new FavoriteDbAdapter(this);
        historyDbAdapter = new HistoryDbAdapter(this);
        
    	/*
    	 * Reset view
    	 */
        stationListAdapter.clear();
    	favoriteDbAdapter.addFavoritesToList(stationListAdapter.getList());
    	historyDbAdapter.addHistoryToList(stationListAdapter.getList());
    	refresh();
    	
    	/*
    	 * And searchbox
    	 */
    	final EditText searchEdit = (EditText) findViewById(R.id.search);
    	searchEdit.setText("");
	}

	/*
	 * Select list item
	 * @see android.app.ListActivity#onListItemClick(android.widget.ListView, android.view.View, int, long)
	 */
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		/*
		 * Take current selected station, and return with it.
		 */
		StationData station = (StationData) stationListAdapter.getItem(position);
		stationSelected(station);
	}
	
	/*
	 * Custom handler for station selected
	 */
	public abstract void stationSelected(StationData station);
	
	public void updateHistory(StationData station) {
		if (!station.isFavorite) {
			historyDbAdapter.updateHistory(station);
		} else {
			favoriteDbAdapter.updateUsed(station);
		}
	}
	
	/*
	 * Custom function for set progress bar status
	 */
	public abstract void setProgressBar(boolean value);
	    
	/*
	 * Refresh view, this involves checking list against current favorites and setting .isFavorite to render star.
	 */
	private void refresh() {
		boolean dbClosed = !favoriteDbAdapter.isOpen();
		if (dbClosed) {
			/*
			 * This can happen as we allow background loading.
			 */
			favoriteDbAdapter.open();
			historyDbAdapter.open();
		}
		favoriteDbAdapter.refreshFavorites(stationListAdapter.getList());
		stationListAdapter.notifyDataSetChanged();
		infoText.setVisibility(stationListAdapter.getCount() > 0 ? View.GONE : View.VISIBLE);
		if (dbClosed) {
			favoriteDbAdapter.close();
			historyDbAdapter.close();
		}
	}
	
	/*
	 * activityResult handles data passed from map view
	 * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
	 */
    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (resultCode == RESULT_OK) {
			/*
			 * return from map view, will return a single station
			 */
			if (data.hasExtra(StationData.PARCELABLE)) {
				/*
				 * We got station in return
				 */
				final StationData station = data.getParcelableExtra(StationData.PARCELABLE);
				onResume(); // reopen database
				stationSelected(station);
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	/*
	 * Save state, commit to databases
	 * @see android.app.Activity#onPause()
	 */
	@Override
	protected void onPause() {
		favoriteDbAdapter.close();
		historyDbAdapter.close();
		if (activeTask != null) {
			activeTask.stop();
		}
		super.onPause();
	}
	
	@Override
	protected void onDestroy() {
		searchProvider.Stop();
		super.onDestroy();
	}

	/*
	 * Resume state, restart search.
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();
		favoriteDbAdapter.open();
		historyDbAdapter.open();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelableArrayList(StationListAdapter.KEY_SEARCHSTATIONLIST, stationListAdapter.getList());
	}
}

class StationListAdapter extends BaseAdapter {
	public static final String KEY_SEARCHSTATIONLIST = "searchstationlist";
	private LayoutInflater inflater;
	private ArrayList<StationData> items = new ArrayList<StationData>();
	private int layout = R.layout.selectstation_list;
	private GenericSelectStationView parent;
	
	public StationListAdapter(GenericSelectStationView parent) {
		this.parent = parent;
		inflater = LayoutInflater.from(parent);
	}

	/*
	 * Simple functions dealing with adding/setting items. 
	 */
	public void clear() { items.clear(); }
	public ArrayList<StationData> getList() { return items; }
	public void setList(ArrayList<StationData> items) { this.items = items; }
	public void addItem(StationData item) { items.add(item); }
	
	/*
	 * Standard android.widget.Adapter items, self explanatory.
	 */
	@Override
	public int getCount() {	return items.size(); }
	@Override
	public Object getItem(int pos) { return items.get(pos); }
	@Override
	public long getItemId(int pos) { return pos; }
	
	/*
	 * Code to support multiselect
	 */
	public void setLayout(int layout) {
		this.layout = layout;
		notifyDataSetChanged();
	}
	
	/*
	 * Setup the view
	 * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
	 */
	@Override
	public View getView(int pos, View convertView, ViewGroup arg2) {
		final StationData station = items.get(pos);
		/*
		 * Setup holder, for performance and readability.
		 */
		ViewHolder holder;
		if (convertView == null || convertView.getId() != layout) {
			/*
			 * New view, inflate and setup holder.
			 */
			convertView = inflater.inflate(layout, null);
			convertView.setId(layout);
			
			holder = new ViewHolder();
			holder.star = (ImageView) convertView.findViewById(R.id.star);
			holder.stopName = (TextView) convertView.findViewById(R.id.stopname);
			holder.address = (TextView) convertView.findViewById(R.id.address);
			holder.range = (TextView) convertView.findViewById(R.id.range);
			convertView.setTag(holder);
		} else {
			/*
			 * Old view found, we can reuse that instead of inflating.
			 */
			holder = (ViewHolder) convertView.getTag();
		}
		
		if (layout == R.layout.selectstation_list_multiselect) {
			CheckBox stopCheckBox = (CheckBox) convertView.findViewById(R.id.stopname);
			stopCheckBox.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					parent.stationSelected(station);
				}
			});
		}
		
		/*
		 * Render data to view.
		 */
		holder.stopName.setText(station.stopName);
		
		/*
		 * Setup station.extra
		 */
		if (station.extra != null) {
			holder.address.setText(station.extra);
			holder.address.setVisibility(View.VISIBLE);
		} else {
			//holder.address.setText("");
			holder.address.setVisibility(View.INVISIBLE);
		}
		
		/*
		 * Setup station.walkingDistance
		 */
		if (station.walkingDistance > 0) {
			holder.range.setText("" + station.walkingDistance + "m");
			holder.range.setVisibility(View.VISIBLE);
		} else {
			//holder.range.setText("");
			holder.range.setVisibility(View.INVISIBLE);
		}
		
		/*
		 * Setup station.isFavorite
		 */
		if (station.isFavorite) {
			holder.star.setVisibility(View.VISIBLE);
		} else {
			holder.star.setVisibility(View.GONE);
		}
		
		return convertView;
	}

	/*
	 * Class for caching the view.
	 */
	static class ViewHolder {
		ImageView star;
		TextView stopName;
		TextView address;
		TextView range;
	}
};

