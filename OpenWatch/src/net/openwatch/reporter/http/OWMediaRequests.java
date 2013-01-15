package net.openwatch.reporter.http;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import net.openwatch.reporter.constants.Constants;
import net.openwatch.reporter.model.OWVideoRecording;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

public class OWMediaRequests {
	
	private static final String TAG = "OWMediaServiceRequests";
	
	/**
	 * POSTs the start signal to the OW NodeMediaCapture Service
	 * @param upload_token the public upload token
	 * @param recording_id recording id generated by client
	 * @param recording_start when the recording started in unix time (seconds)
	 */
	public static void start(String upload_token, String recording_id, String recording_start){
		AsyncHttpClient client = HttpClient.setupHttpClient();
		RequestParams params = new RequestParams();
		params.put(Constants.OW_REC_START, recording_start);
		String url = setupMediaURL(Constants.OW_MEDIA_START, upload_token, recording_id);
		Log.i(TAG, "sending start to " + url);
		client.post(url, params, new AsyncHttpResponseHandler(){

    		@Override
    		public void onSuccess(String response){
    			Gson gson = new Gson();
    			Map<Object,Object> map = new HashMap<Object,Object>();
    			try{
	    			map = gson.fromJson(response, map.getClass());
    			} catch(Throwable t){
    				Log.e(TAG, "Error parsing response. 500 error?");
    				onFailure(new Throwable(), "Error parsing server response");
    				return;
    			}
    			
    			if( (Boolean)map.get(Constants.OW_SUCCESS) == true){
    				Log.i(TAG,"start signal success: " +  map.toString());
    		        return;
    			} else{
    				Log.i(TAG,"start signal server error: " +  map.toString());
    			}

    		}
    		
    		@Override
    	     public void onFailure(Throwable e, String response) {
    			Log.i(TAG,"start signal failure: " +  response);
    	     }
    		
    		@Override
    	     public void onFinish() {
    	        Log.i(TAG,"start signal finished");
    	     }

		});
	}
	
	/**
	 * POSTs a LQ video chunk to the OW MediaCapture Service
	 * @param upload_token the public upload token
	 * @param recording_id the unique recording id generated by this client
	 * @param filename the video file chunk to upload
	 */
	public static void sendLQChunk(String upload_token, String recording_id, String filename){
		AsyncHttpClient client = HttpClient.setupHttpClient();
		File file = new File(filename);
		RequestParams params = new RequestParams();
		try {
		    params.put(Constants.OW_FILE, file);
		} catch(FileNotFoundException e) {
			Log.e(TAG, filename + " not found");
			return;
		}
		String url = setupMediaURL(Constants.OW_MEDIA_UPLOAD, upload_token, recording_id);
		Log.i(TAG, "sending video chunk " + filename + " to " + url);
		client.post(url, params, new AsyncHttpResponseHandler(){

    		@Override
    		public void onSuccess(String response){
    			Gson gson = new Gson();
    			Map<Object,Object> map = new HashMap<Object,Object>();
    			try{
	    			map = gson.fromJson(response, map.getClass());
    			} catch(Throwable t){
    				Log.e(TAG, "Error parsing chunk response. 500 error?");
    				onFailure(new Throwable(), "Error parsing server response");
    				return;
    			}
    			
    			if( (Boolean)map.get(Constants.OW_SUCCESS) == true){
    				Log.i(TAG,"chunk signal success: " +  map.toString());
    		        return;
    			} else{
    				Log.i(TAG,"chunk signal server error: " +  map.toString());
    			}

    		}
    		
    		@Override
    		public void onFailure(Throwable e, String response){
    			Log.i(TAG,"chunk signal failure: " +  response);
    			e.printStackTrace();
    	     }
    		
    		@Override
    		public void onFinish(){
    			Log.i(TAG, "chunk signal finished");
    		}

		});
	}
	
	/**
	 * POSTs an end signal to the OW MediaCapture Service
	 * @param upload_token
	 * @param recording_id
	 * @param recording_start
	 * @param recording_end
	 * @param all_files
	 */
	public static void end(Context c, String upload_token, OWVideoRecording recording, String all_files){
		AsyncHttpClient client = HttpClient.setupHttpClient();
		RequestParams params = initializeRequestParamsWithLocalRecording(c, recording);
		params.put(Constants.OW_ALL_FILES, all_files);
		String url = setupMediaURL(Constants.OW_MEDIA_END, upload_token, recording.uuid.get());
		Log.i(TAG, "sending end signal to " + url);
		client.post(url, params, new AsyncHttpResponseHandler(){

    		@Override
    		public void onSuccess(String response){
    			Gson gson = new Gson();
    			Map<Object,Object> map = new HashMap<Object,Object>();
    			try{
	    			map = gson.fromJson(response, map.getClass());
    			} catch(Throwable t){
    				Log.e(TAG, "Error parsing response. 500 error?");
    				onFailure(new Throwable(), "Error parsing server response");
    				return;
    			}
    			
    			if( (Boolean)map.get(Constants.OW_SUCCESS) == true){
    				Log.i(TAG,"end signal success: " +  map.toString());
    		        return;
    			} else{
    				Log.i(TAG,"end signal server error: " +  map.toString());
    			}

    		}
    		
    		@Override
    	     public void onFailure(Throwable e, String response) {
    			Log.i(TAG,"end signal failure: " +  response);
    			e.printStackTrace();
    	     }

		});
	}
	
	public static void updateMeta(Context c, String upload_token, OWVideoRecording recording){
		AsyncHttpClient client = HttpClient.setupHttpClient();
		RequestParams params = initializeRequestParamsWithLocalRecording(c, recording);
		Log.i(TAG, "updateMeta: " + params.toString());
		String url = setupMediaURL(Constants.OW_MEDIA_UPDATE_META, upload_token, recording.uuid.get());
		
		client.post(url, params, new AsyncHttpResponseHandler(){

    		@Override
    		public void onSuccess(String response){
    			Log.i(TAG, "got meta response " + response);

    		}
    		
    		@Override
    	     public void onFailure(Throwable e, String response) {
    			Log.i(TAG,"end signal failure: " +  response);
    			e.printStackTrace();
    	     }

		});
	}
	
	/**
	 * POST the hq video to the OW MediaCapture service
	 * @param upload_token
	 * @param recording_id
	 * @param filename
	 */
	public static void sendHQFile(String upload_token, String recording_id, String filename){
		AsyncHttpClient client = HttpClient.setupHttpClient();
		File file = new File(filename);
		RequestParams params = new RequestParams();
		try {
		    params.put(Constants.OW_FILE, file);
		} catch(FileNotFoundException e) {
			Log.e(TAG, filename + " not found");
			return;
		}
		String url = setupMediaURL(Constants.OW_MEDIA_HQ_UPLOAD, upload_token, recording_id);
		Log.i(TAG, "sending hq video to " + url);
		client.post(url, params, new AsyncHttpResponseHandler(){

    		@Override
    		public void onSuccess(String response){
    			Gson gson = new Gson();
    			Map<Object,Object> map = new HashMap<Object,Object>();
    			try{
	    			map = gson.fromJson(response, map.getClass());
    			} catch(Throwable t){
    				Log.e(TAG, "Error parsing hq response. 500 error?");
    				onFailure(new Throwable(), "Error parsing server response");
    				return;
    			}
    			
    			if( (Boolean)map.get(Constants.OW_SUCCESS) == true){
    				Log.i(TAG,"hq signal success: " +  map.toString());
    		        return;
    			} else{
    				Log.i(TAG,"hq signal server error: " +  map.toString());
    			}

    		}
    		
    		@Override
    	     public void onFailure(Throwable e, String response) {
    			Log.i(TAG,"hq signal failure: " +  response);
    			e.printStackTrace();
    	     }
    		
    		@Override
	   	     public void onFinish() {
	   			Log.i(TAG,"hq signal finished ");
	   	     }

		});
	}
	
	private static String setupMediaURL(String endpoint, String public_upload_token, String recording_id){
		return Constants.OW_MEDIA_URL + endpoint + "/" + public_upload_token + "/" + recording_id;
	}
	
	private static RequestParams initializeRequestParamsWithLocalRecording(Context c, OWVideoRecording recording){
		RequestParams params = new RequestParams();
		if(recording.begin_lat.get() != 0){
			Log.i(TAG, "sending START GEO: " + recording.begin_lat.get().toString() + ", " + recording.begin_lon.get().toString());
			params.put(Constants.OW_START_LOC + "[" + Constants.OW_LAT + "]", recording.begin_lat.get().toString());
			params.put(Constants.OW_START_LOC + "[" + Constants.OW_LON + "]", recording.begin_lon.get().toString());

		}
		if(recording.end_lat.get() != 0){
			Log.i(TAG, "sending END GEO: " + recording.end_lat.get().toString() + ", " + recording.end_lon.get().toString());
			params.put(Constants.OW_END_LOC + "[" + Constants.OW_LAT + "]", recording.end_lat.get().toString());
			params.put(Constants.OW_END_LOC + "[" + Constants.OW_LON + "]", recording.end_lon.get().toString());

		}
		if(recording.getTitle(c) != null && recording.getTitle(c).compareTo("") != 0){
			params.put(Constants.OW_MEDIA_TITLE, recording.getTitle(c));
		}
		if(recording.getDescription(c) != null){
			params.put(Constants.OW_DESCRIPTION, recording.getDescription(c));
		}
		return params;
	}

}
