/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ale.openwatch.feeds;

import android.*;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.*;
import android.widget.Filter;
import com.orm.androrm.*;
import org.ale.openwatch.*;
import org.ale.openwatch.R;
import org.ale.openwatch.constants.Constants;
import org.ale.openwatch.constants.DBConstants;
import org.ale.openwatch.contentprovider.OWContentProvider;
import org.ale.openwatch.http.OWServiceRequests;
import org.ale.openwatch.http.OWServiceRequests.PaginatedRequestCallback;
import org.ale.openwatch.location.DeviceLocation;
import org.ale.openwatch.location.DeviceLocation.GPSRequestCallback;
import org.ale.openwatch.model.OWServerObject;

import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SearchViewCompat.OnQueryTextListenerCompat;
import android.util.Log;
import android.view.*;
import android.widget.AbsListView.OnScrollListener;
import org.ale.openwatch.model.OWServerObjectInterface;
import org.ale.openwatch.model.OWUser;
import org.ale.openwatch.model.OWVideoRecording;
import org.ale.openwatch.share.Share;

/**
 * Demonstration of the implementation of a custom Loader.
 */
public class RemoteFeedFragmentActivity extends FragmentActivity {
    private static final String TAG = "RemoteFeedFragmentActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentManager fm = getSupportFragmentManager();

        // Create the list fragment and add it as our sole content.
        if (fm.findFragmentById(android.R.id.content) == null) {
            RemoteRecordingsListFragment list = new RemoteRecordingsListFragment();
            fm.beginTransaction().add(android.R.id.content, list).commit();
        }
        
    }


    public static class RemoteRecordingsListFragment extends ListFragment
            implements LoaderManager.LoaderCallbacks<Cursor> {
    	
    	static String TAG = "RemoteFeedFragment";
    	boolean didRefreshFeed = false;
    	int page = 0;
    	boolean has_next_page = false;
        boolean fetching_next_page = false;

        int internal_user_id = -1;

        View loading_footer;
    	
    	String feed;
    	Location device_location;
    	Uri this_uri; // TESTING

        // This is the Adapter being used to display the list's data.
        //AppListAdapter mAdapter;
    	OWMediaObjectAdapter mAdapter;

        // If non-null, this is the current filter the user has provided.
        String mCurFilter;

        VideoView videoView;
        ProgressBar progressBar;
        ViewGroup videoViewParent;
        int videoViewListIndex;

        OnQueryTextListenerCompat mOnQueryTextListenerCompat;
        
        PaginatedRequestCallback cb = new PaginatedRequestCallback(){

			@Override
			public void onSuccess(int page, int object_count, int total_pages) {
				if(RemoteRecordingsListFragment.this.isAdded()){
					RemoteRecordingsListFragment.this.page = page;
					if(total_pages <= page)
						RemoteRecordingsListFragment.this.has_next_page = false;
					else
						RemoteRecordingsListFragment.this.has_next_page = true;
					didRefreshFeed = true;
                    fetching_next_page = false;
                    showLoadingMore(false);
					restartLoader();
				}
			}

			@Override
			public void onFailure(int page) {}
        	
        };

        @Override public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            // Give some text to display if there is no data.  In a real
            // application this would come from a resource.
            setEmptyText(getString(R.string.feed_empty));

            // We have a menu item to show in action bar.
            setHasOptionsMenu(true);

            // Initialize adapter without cursor. Let loader provide it when ready
            mAdapter = new OWMediaObjectAdapter(getActivity(), null);
            // Add footer loading view
            LayoutInflater layoutInflater = (LayoutInflater) getActivity().getSystemService(LAYOUT_INFLATER_SERVICE);
            loading_footer = layoutInflater.inflate(R.layout.list_view_loading_footer, (ViewGroup) getActivity().findViewById(android.R.id.list), false);
            loading_footer.setVisibility(View.GONE);
            getListView().addFooterView(loading_footer);
            setListAdapter(mAdapter);
            getListView().setDivider(null);
            getListView().setDividerHeight(0);

            this.getListView().setOnScrollListener(new OnScrollListener(){

				@Override
				public void onScrollStateChanged(AbsListView view,
						int scrollState) {}

				@Override
				public void onScroll(AbsListView view, int firstVisibleItem,
						int visibleItemCount, int totalItemCount) {

                    if(videoViewListIndex != -1 && !(videoViewListIndex >= firstVisibleItem && videoViewListIndex <= firstVisibleItem + visibleItemCount)){

                        if(progressBar != null){
                            progressBar.setVisibility(View.GONE);
                            videoViewParent.removeView(progressBar);
                            //progressBar = null;
                            //Log.i(TAG, "progressBar is hidden");
                        }else{
                            //Log.i(TAG, "progressBar is null");
                        }
                        if(videoView != null){
                            Log.i(TAG, "Removing VideoView from list");
                            videoView.setVisibility(View.GONE);
                            videoView.stopPlayback();
                            videoViewParent.removeView(videoView);
                            //videoView = null;
                            //videoViewParent = null;
                        }
                    }

					if(!RemoteRecordingsListFragment.this.has_next_page)
						return;
					
					boolean loadMore = firstVisibleItem + visibleItemCount >= totalItemCount;

				        if(loadMore) {
				            RemoteRecordingsListFragment.this.fetchNextFeedPage();
				        }

				}
            	
            });

            // Start out with a progress indicator.
            setListShown(false);
            
            feed = this.getArguments().getString(Constants.OW_FEED);
            Log.i(TAG, "got feed name: " +  feed.toString() );
            
            // Prepare the loader.  Either re-connect with an existing one,
            // or start a new one.
           
            // Refresh the feed view
            if(!didRefreshFeed){
	            // If our feed demands device location and we haven't cached it
	            if(Constants.isOWFeedTypeGeoSensitive(feed) && device_location == null){
	            	GPSRequestCallback gps_callback = new GPSRequestCallback(){
	
						@Override
						public void onSuccess(Location result) {
							device_location = result;
							fetchNextFeedPage();
                        }
	            	};
	            	DeviceLocation.getLastKnownLocation(getActivity().getApplicationContext(), false, gps_callback);
	            }else{
	            	fetchNextFeedPage();
	            }
        	}
            if(feed.compareTo(Constants.OWFeedType.USER.toString().toLowerCase()) == 0)
                checkUserState();

        }
        
        private void fetchNextFeedPage(){
            if(!fetching_next_page){
                if(Constants.isOWFeedTypeGeoSensitive(feed) && device_location != null){
                    try{
                        OWServiceRequests.getGeoFeed(this.getActivity().getApplicationContext(), device_location, feed, page+1, cb);	 // NPE HERE
                        fetching_next_page = true;
                        showLoadingMore(true);
                    }catch(NullPointerException e){
                        Log.e(TAG, "NPE getting GeoFeed");
                        e.printStackTrace();
                    }
                }
                else{
                    try{
                        OWServiceRequests.getFeed(this.getActivity().getApplicationContext(), feed, page+1, cb);
                        fetching_next_page = true;
                        showLoadingMore(true);
                    }catch(NullPointerException e){
                        Log.e(TAG, "NPE getting GeoFeed");
                        e.printStackTrace();
                    }
                }
            }
        }
        
        private void restartLoader(){
        	this.getLoaderManager().restartLoader(0, null, this);
        }

        @Override public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            // Place an action bar item for searching.
        	/*
            MenuItem item = menu.add("Search");
            item.setIcon(android.R.drawable.ic_menu_search);
            MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM
                    | MenuItemCompat.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
            View searchView = SearchViewCompat.newSearchView(getActivity());
            if (searchView != null) {
                SearchViewCompat.setOnQueryTextListener(searchView,
                        new OnQueryTextListenerCompat() {
                    @Override
                    public boolean onQueryTextChange(String newText) {
                        // Called when the action bar search text has changed.  Since this
                        // is a simple array adapter, we can just have it do the filtering.
                        mCurFilter = !TextUtils.isEmpty(newText) ? newText : null;
                        mAdapter.getFilter().filter(mCurFilter);
                        return true;
                    }
                });
                MenuItemCompat.setActionView(item, searchView);
            }
            */
        }

        OWUtils.VideoViewCallback videoViewCallback = new OWUtils.VideoViewCallback() {
            @Override
            public void onPlaybackComplete(ViewGroup parent) {
                Log.i(TAG, "playbackComplete");
                parent.removeView(parent.findViewById(R.id.videoView));
                //parent.removeView(parent.findViewById(R.id.videoProgress));
                parent.findViewById(R.id.thumbnail).setVisibility(View.VISIBLE);
                parent.findViewById(R.id.playButton).setVisibility(View.VISIBLE);
            }

            @Override
            public void onPrepared(ViewGroup parent) {
                Log.i(TAG, "onPrepared");
                //parent.findViewById(R.id.videoProgress).setVisibility(View.GONE);
                //progressBar.setVisibility(View.GONE);
                parent.removeView(parent.findViewById(R.id.videoProgress));
                if(Build.VERSION.SDK_INT >= 11)
                    videoView.setAlpha(1);
            }

            @Override
            public void onError(ViewGroup parent) {
                this.onPlaybackComplete(parent);
            }
        };

        @Override public void onListItemClick(ListView l, View v, int position, long id) {
            Log.i("LoaderCustom", "Item clicked: " + id);
        	try{
        		final int model_id = (Integer)v.getTag(R.id.list_item_model);
        		final OWServerObject server_object = OWServerObject.objects(getActivity().getApplicationContext(), OWServerObject.class).get(model_id);

                if(v.getTag(R.id.subView) != null && v.getTag(R.id.subView).toString().compareTo("menu") == 0){
                    Log.i(TAG, "menu click!");
                    final Context c = getActivity();
                    LayoutInflater inflater = (LayoutInflater)
                            getActivity().getSystemService(LAYOUT_INFLATER_SERVICE);
                    View layout = inflater.inflate(R.layout.media_menu_popup,
                            (ViewGroup) getActivity().findViewById(R.id.content_frame), false);
                    final AlertDialog dialog =  new AlertDialog.Builder(getActivity()).setView(layout).create();
                    layout.findViewById(R.id.shareButton).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                            Share.showShareDialogWithInfo(c, getString(R.string.share_video), server_object.getTitle(c), OWUtils.urlForOWServerObject(server_object, c));
                            OWServiceRequests.increaseHitCount(c, server_object.getServerId(c), model_id, server_object.getContentType(c), Constants.HIT_TYPE.CLICK);
                        }
                    });
                    if(((OWServerObjectInterface) server_object.getChildObject(c)).getLat(c) != 0.0 ){
                        layout.findViewById(R.id.mapButton).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                dialog.dismiss();
                                Intent i = new Intent(getActivity(), MapActivity.class);
                                i.putExtra(Constants.INTERNAL_DB_ID, model_id);
                                startActivity(i);
                            }
                        });
                    }else
                        layout.findViewById(R.id.mapButton).setVisibility(View.GONE);
                    layout.findViewById(R.id.reportButton).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                            OWServiceRequests.flagOWServerObjet(getActivity().getApplicationContext(), server_object);
                        }
                    });
                    dialog.show();
                    return;
                }else
                    Log.i(TAG, "non menu click!");
        		
        		Intent i = null;
        		switch(server_object.getContentType(getActivity().getApplicationContext())){
        		case STORY:
        			i = new Intent(this.getActivity(), OWStoryViewActivity.class);
        			break;
        		case INVESTIGATION:
        			//TODO: InvestigationViewActivity
        			i = new Intent(this.getActivity(), OWInvestigationViewActivity.class);
        			break;
        		case VIDEO:
                    // play video inline
                    v.findViewById(R.id.playButton).setVisibility(View.GONE);
                    LayoutInflater layoutInflater = (LayoutInflater) getActivity().getSystemService(LAYOUT_INFLATER_SERVICE);
                    //videoViewParent = (ViewGroup) v;
                    videoViewParent = (ViewGroup) layoutInflater.inflate(R.layout.feed_video_view, (ViewGroup) v, true);
                    videoView = (VideoView) videoViewParent.findViewById(R.id.videoView);
                    if(Build.VERSION.SDK_INT >= 11)
                        videoView.setAlpha(0);
                    progressBar = (ProgressBar) videoViewParent.findViewById(R.id.videoProgress);
                    progressBar.setVisibility(View.VISIBLE);

                    Log.i(TAG, progressBar.toString());
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)videoView.getLayoutParams();
                    String url = ((OWVideoRecording) server_object.getChildObject(getActivity().getApplicationContext())).getMediaFilepath(getActivity().getApplicationContext());
                    OWUtils.setupVideoView(getActivity(), v.findViewById(R.id.thumbnail), videoView, url, videoViewCallback, progressBar);
                    videoViewListIndex = position;
        			break;
                case AUDIO:
                case PHOTO:
                    i = new Intent(this.getActivity(), OWMediaObjectViewActivity.class);
                    break;
                case MISSION:
                    i = new Intent(this.getActivity(), OWMissionViewActivity.class);
        		}

        		if(i != null){
                    i.putExtra(Constants.INTERNAL_DB_ID, (Integer)v.getTag(R.id.list_item_model));
        			startActivity(i);
                }
        	}catch(Exception e){
        		Log.e(TAG, "failed to load list item model tag");
                e.printStackTrace();
        		return;
        	}
        	
        }


        private void showLoadingMore(boolean show){
            if(show){
                loading_footer.setVisibility(View.VISIBLE);
            }else{
                loading_footer.setVisibility(View.GONE);
            }
        }

        
		@Override
		public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
			mAdapter.swapCursor(cursor);
			if(!isAdded())
				return;
			/*
			if(cursor == null || cursor.getCount() == 0)
				Log.i("URI" + feed.toString(), "onLoadFinished empty cursor on uri " + this_uri.toString());
			else
				Log.i("URI" + feed.toString(), String.format("onLoadFinished %d rows on uri %s ",cursor.getCount(), this_uri.toString()));
			*/
			// The list should now be shown.
            if (isResumed()) {
                setListShown(true);
            } else {
                setListShownNoAnimation(true);
            }
            
           if(cursor != null && cursor.getCount() == 0){
        		setEmptyText(getString(R.string.feed_empty));
           }
			
		}

		@Override
		public void onLoaderReset(Loader<Cursor> arg0) {
			// TODO Auto-generated method stub
			Log.i("URI", "onLoaderReset on " + feed.toString());
			mAdapter.swapCursor(null);
		}
		
		static final String[] PROJECTION = new String[] {
			DBConstants.ID,
			DBConstants.RECORDINGS_TABLE_TITLE,
			DBConstants.VIEWS,
			DBConstants.ACTIONS,
			DBConstants.RECORDINGS_TABLE_THUMB_URL,
			DBConstants.RECORDINGS_TABLE_USERNAME,
            DBConstants.MEDIA_OBJECT_STORY,
            DBConstants.MEDIA_OBJECT_AUDIO,
            DBConstants.MEDIA_OBJECT_VIDEO,
            DBConstants.MEDIA_OBJECT_PHOTO,
            DBConstants.MEDIA_OBJECT_INVESTIGATION,
            DBConstants.MEDIA_OBJECT_MISSION,
            DBConstants.MEDIA_OBJECT_USER_THUMBNAIL,
            DBConstants.LAST_EDITED,
            DBConstants.MEDIA_OBJECT_METRO_CODE

	    };

		@Override
		public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
            Uri baseUri = null;
            if(feed.compareTo(Constants.OWFeedType.USER.toString().toLowerCase()) == 0)
                baseUri = OWContentProvider.getUserRecordingsUri(internal_user_id);
            else
                baseUri = OWContentProvider.getFeedUri(feed);
			this_uri = baseUri;
			String selection = null;
            String[] selectionArgs = null;
            String order = null;
			Log.i("URI"+feed.toString(), "createLoader on uri: " + baseUri.toString());
			return new CursorLoader(getActivity(), baseUri, PROJECTION, selection, selectionArgs, order);
		}

        public void checkUserState(){
            SharedPreferences profile = getActivity().getSharedPreferences(Constants.PROFILE_PREFS, 0);
            boolean authenticated = profile.getBoolean(Constants.AUTHENTICATED, false);
            if(authenticated){
                int user_server_id = profile.getInt(DBConstants.USER_SERVER_ID, 0);
                //int user_server_id = (Integer) OWApplication.user_data.get(DBConstants.USER_SERVER_ID);
                com.orm.androrm.Filter filter = new com.orm.androrm.Filter();
                filter.is(DBConstants.USER_SERVER_ID, user_server_id);
                QuerySet<OWUser> users = OWUser.objects(getActivity().getApplicationContext(), OWUser.class).filter(filter);
                for(OWUser user : users){
                    internal_user_id = user.getId();
                    break;
                }
                if(internal_user_id > 0){
                    setEmptyText(getString(R.string.loading_recordings));
                    setListShown(false); // start with a progress indicator
                    getLoaderManager().initLoader(0, null, this);
                }
            }else{
                // It's possible the sharedpreferences haven't finished being written to, but the user has logged in
                if( OWApplication.user_data != null && OWApplication.user_data.containsKey(Constants.AUTHENTICATED)){
                    setEmptyText(getString(R.string.loading_recordings));
                }else
                    setEmptyText(getString(R.string.login_for_local_recordings));
            }
        }
    }


}