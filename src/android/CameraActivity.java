package com.litekey.cordova.plugins.heartbeat;


import org.apache.cordova.CordovaActivity;

import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;

public class CameraActivity extends CordovaActivity {

	private static final String TAG = "CameraActivity";
	
	private int fps;
	private int seconds;
	private Camera camera;
	private ForegroundCameraPreview preview;
	private HeartBeatDetection detection;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		seconds = getIntent().getExtras().getInt(HeartBeatPlugin.SECONDS_KEY);
		fps = getIntent().getExtras().getInt(HeartBeatPlugin.FPS_KEY);
		
		Log.i(TAG, "seconds: "+ seconds + ", fps: " + fps);

		setContentView(getResources().getIdentifier("foregroundcamera",
				"layout", getPackageName()));

		camera = getCameraInstance();
		try {
			Camera.Parameters params = camera.getParameters();
			params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
			params.setPreviewFormat(ImageFormat.YV12);
			params.setPreviewFpsRange(fps * 1000,
					fps * 1000);
			camera.setParameters(params);
			camera.setPreviewCallback(previewCallback);
		} catch (Exception e) {
			Log.e(TAG, "Camera error", e);
			setResult(RESULT_CANCELED);
			finish();
			return;
		}

		detection = new HeartBeatDetection(camera);

		preview = new ForegroundCameraPreview(this, camera);

		LinearLayout layout = (LinearLayout) findViewById(getResources()
				.getIdentifier("camera_preview", "id", getPackageName()));

		int width = camera.getParameters().getPreviewSize().width;
		int height = camera.getParameters().getPreviewSize().height;
		layout.setTranslationX(width);
		layout.setTranslationY(height);

		ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		layout.addView(preview, layoutParams);

	}

	public static Camera getCameraInstance() {
		int numCameras = Camera.getNumberOfCameras();
		if (numCameras == 0) {
			Log.w(TAG, "No cameras!");
			return null;
		}
		int index = 0;
		while (index < numCameras) {
			Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
			Camera.getCameraInfo(index, cameraInfo);
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
				break;
			}
			index++;
		}
		Camera camera;
		if (index < numCameras) {
			Log.i(TAG, "Opening camera #" + index);
			camera = Camera.open(index);
		} else {
			Log.i(TAG, "No camera facing back; returning camera #0");
			camera = Camera.open(0);
		}
		return camera;
	}

	@Override
	public void finish() {
		if (camera != null) {
			try {
				Camera.Parameters params = camera.getParameters();
				params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
				camera.setParameters(params);
				camera.stopPreview();
				camera.setPreviewCallback(null);
			} catch (Exception e) {
				Log.d(TAG, "Exception stopping camera: " + e.getMessage());
			}
			camera.release();
			camera = null;
		}
		super.finish();
	}

	private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {

		@Override
		public void onPreviewFrame(byte[] buf, Camera camera) {
			
			detection.analyzeFrame(buf);
			
			if (detection.getFramesAnalyzed() == (seconds * fps)) {
				Intent intent = new Intent();
				intent.putExtra(HeartBeatPlugin.BPM_KEY,
						detection.getHeartBeat());
				CameraActivity.this.setResult(RESULT_OK, intent);
				CameraActivity.this.finish();
			}

		}
	};

}