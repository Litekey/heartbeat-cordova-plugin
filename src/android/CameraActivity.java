package com.litekey.cordova.plugins.heartbeat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.cordova.CordovaActivity;

import android.content.Intent;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;

public class CameraActivity extends CordovaActivity {

	private static final String TAG = "CameraActivity";
	private static final int FRAMES_PER_SECOND = 30;
	private static final int SECONDS = 10;

	private List<Double> dataPointsHue;
	private List<Integer> bpms;
	private int[] rgb;
	private int width;
	private int height;
	private int widthScaleFactor;
	private int heightScaleFactor;
	private double scaleFactor;

	private Camera camera;
	private ForegroundCameraPreview preview;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(getResources().getIdentifier("foregroundcamera",
				"layout", getPackageName()));

		camera = getCameraInstance();
		if (camera == null) {
			setResult(RESULT_CANCELED);
			finish();
			return;
		}
		Camera.Parameters params = camera.getParameters();
		params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
		params.setPreviewFpsRange(FRAMES_PER_SECOND * 1000,
				FRAMES_PER_SECOND * 1000);
		camera.setParameters(params);
		camera.setPreviewCallback(previewCallback);

		width = camera.getParameters().getPictureSize().width;
		height = camera.getParameters().getPictureSize().height;

		preview = new ForegroundCameraPreview(this, camera);

		LinearLayout layout = (LinearLayout) findViewById(getResources()
				.getIdentifier("camera_preview", "id", getPackageName()));
		
		layout.setTranslationX(width);
		layout.setTranslationY(height);
		
		ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		layout.addView(preview, layoutParams);

		configure();

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

	private void configure() {
		dataPointsHue = new ArrayList<Double>();
		bpms = new ArrayList<Integer>();
		widthScaleFactor = width / (width / 5);
		heightScaleFactor = height / (height / 5);
		scaleFactor = ((double) (width * height) / (double) (heightScaleFactor * widthScaleFactor));
		rgb = new int[width * height];
	}

	private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {

		@Override
		public void onPreviewFrame(byte[] buf, Camera camera) {

			decodeYUV420SP(rgb, buf, width, height);
			int r = 0, g = 0, b = 0;

			for (int y = 0; y < height; y += heightScaleFactor) {
				for (int x = 0; x < width; x += widthScaleFactor) {
					r += (rgb[(y * width) + x] >> 16) & 0x0ff;
					g += (rgb[(y * width) + x] >> 8) & 0x0ff;
					b += rgb[(y * width) + x] & 0x0ff;
				}
			}

			r /= scaleFactor;
			g /= scaleFactor;
			b /= scaleFactor;

			float[] hsv = new float[3];
			Color.RGBToHSV(r, g, b, hsv);

			dataPointsHue.add((double) hsv[0]);

			if (dataPointsHue.size() % FRAMES_PER_SECOND == 0) {

				double[] bandpassFilteredItems = butterworthBandpassFilter(dataPointsHue);
				double[] smoothedBandpassItems = medianSmoothing(bandpassFilteredItems);
				int peakCount = countPeaks(smoothedBandpassItems);

				double secondsPassed = smoothedBandpassItems.length
						/ FRAMES_PER_SECOND;
				double percentage = secondsPassed / 60;
				int bpm = (int) (peakCount / percentage);

				Log.i(TAG, "Actual bpm:" + bpm);
				bpms.add(bpm);
				
			}

			if (dataPointsHue.size() == (SECONDS * FRAMES_PER_SECOND)) {
				Collections.sort(bpms);
				int bpm = bpms.get(bpms.size() / 2);
				Log.i(TAG, "Result bpm:" + bpm);
				Intent intent = new Intent();
				intent.putExtra("bpm", bpm);
				CameraActivity.this.setResult(RESULT_OK, intent);
				CameraActivity.this.finish();
			}

		}
	};

	private void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width,
			int height) {
		final int frameSize = width * height;
		for (int j = 0, yp = 0; j < height; j++) {
			int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
			for (int i = 0; i < width; i++, yp++) {
				int y = (0xff & ((int) yuv420sp[yp])) - 16;
				if (y < 0)
					y = 0;
				if ((i & 1) == 0) {
					v = (0xff & yuv420sp[uvp++]) - 128;
					u = (0xff & yuv420sp[uvp++]) - 128;
				}

				int y1192 = 1192 * y;
				int r = (y1192 + 1634 * v);
				int g = (y1192 - 833 * v - 400 * u);
				int b = (y1192 + 2066 * u);

				if (r < 0)
					r = 0;
				else if (r > 262143)
					r = 262143;
				if (g < 0)
					g = 0;
				else if (g > 262143)
					g = 262143;
				if (b < 0)
					b = 0;
				else if (b > 262143)
					b = 262143;

				rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000)
						| ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
			}
		}
	}

	// http://www-users.cs.york.ac.uk/~fisher/cgi-bin/mkfscript
	// Butterworth Bandpass filter
	private static final int NZEROS = 8;
	private static final int NPOLES = 8;
	private double[] xv = new double[NZEROS + 1];
	private double[] yv = new double[NPOLES + 1];
	private double[] butterworthBandpassFilter(List<Double> inputData) {

		double dGain = 1.232232910e+02;

		double[] outputData = new double[inputData.size()];
		for (int i = 0; i < outputData.length; i++) {
			double input = inputData.get(i);
			xv[0] = xv[1];
			xv[1] = xv[2];
			xv[2] = xv[3];
			xv[3] = xv[4];
			xv[4] = xv[5];
			xv[5] = xv[6];
			xv[6] = xv[7];
			xv[7] = xv[8];
			xv[8] = input / dGain;
			yv[0] = yv[1];
			yv[1] = yv[2];
			yv[2] = yv[3];
			yv[3] = yv[4];
			yv[4] = yv[5];
			yv[5] = yv[6];
			yv[6] = yv[7];
			yv[7] = yv[8];
			yv[8] = (xv[0] + xv[8]) - 4 * (xv[2] + xv[6]) + 6 * xv[4]
					+ (-0.1397436053 * yv[0]) + (1.2948188815 * yv[1])
					+ (-5.4070037946 * yv[2]) + (13.2683981280 * yv[3])
					+ (-20.9442560520 * yv[4]) + (21.7932169160 * yv[5])
					+ (-14.5817197500 * yv[6]) + (5.7161939252 * yv[7]);

			outputData[i] = yv[8];
		}
		return outputData;
	}

	private int countPeaks(double[] inputData) {
		if (inputData.length == 0) {
			return 0;
		}
		int count = 0;
		for (int i = 3; i < inputData.length - 3;) {
			if (inputData[i] > 0 && inputData[i] > inputData[i - 1]
					&& inputData[i] > inputData[i - 2]
					&& inputData[i] > inputData[i - 3]
					&& inputData[i] >= inputData[i + 1]
					&& inputData[i] >= inputData[i + 2]
					&& inputData[i] >= inputData[i + 3]) {
				count = count + 1;
				i = i + 4;
			} else {
				i = i + 1;
			}
		}
		return count;
	}

	private double[] medianSmoothing(double[] inputData) {
		double[] newData = new double[inputData.length];

		for (int i = 0; i < inputData.length; i++) {
			if (i == 0 || i == 1 || i == 2 || i == inputData.length - 1
					|| i == inputData.length - 2 || i == inputData.length - 3) {
				newData[i] = inputData[i];
			} else {
				double[] items = { inputData[i - 2], inputData[i - 1],
						inputData[i], inputData[i + 1], inputData[i + 2] };
				Arrays.sort(items);
				newData[i] = items[2];
			}
		}

		return newData;
	}

}