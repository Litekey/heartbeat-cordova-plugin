package com.litekey.cordova.plugins.heartbeat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import android.hardware.Camera;

public class HeartBeatDetection {

	private final Object monitor = new Object();

	private int width;
	private int height;

	private List<Float> dataPointsHue;
	private int[] rgb;

	public HeartBeatDetection(Camera camera) {
		width = camera.getParameters().getPreviewSize().width;
		height = camera.getParameters().getPreviewSize().height;
		dataPointsHue = new ArrayList<Float>();
		rgb = new int[width * height];
	}

	public void analyzeFrame(byte[] frame) {
		synchronized (monitor) {

			decodeYUV420SP(rgb, frame, width, height);

			float r = 0, g = 0, b = 0;

			for (int i = 0; i < rgb.length; i++) {
				r += (rgb[i] & 0xff0000) >> 16;
				g += (rgb[i] & 0xff00) >> 8;
				b += (rgb[i] & 0xff);
			}

			r /= 255 * rgb.length;
			g /= 255 * rgb.length;
			b /= 255 * rgb.length;

			float[] hsv = new float[3];
			RGBtoHSV(r, g, b, hsv);
			dataPointsHue.add(hsv[0]);
		}
	}

	public int getHeartBeat(int fps) {
		synchronized (monitor) {
			float[] dataHue = new float[dataPointsHue.size()];
			for (int i = 0; i < dataHue.length; i++) {
				dataHue[i] = dataPointsHue.get(i);
			}
			float[] bandpassFilteredItems = butterworthBandpassFilter(dataHue);
			float[] smoothedBandpassItems = medianSmoothing(bandpassFilteredItems);
			int peak = medianPeak(smoothedBandpassItems);
			int bpm = 60 * fps / peak;
			return bpm;
		}
	}

	private void decodeYUV420SP(int[] rgba, byte[] yuv420sp, int width,
			int height) {
		final int frameSize = width * height;
		int y1192, r, g, b, u, v, uvp;
		for (int j = 0, yp = 0; j < height; j++) {
			uvp = frameSize + (j >> 1) * width;
			u = 0;
			v = 0;
			for (int i = 0; i < width; i++, yp++) {
				int y = (0xff & ((int) yuv420sp[yp])) - 16;
				if (y < 0)
					y = 0;
				if ((i & 1) == 0) {
					v = (0xff & yuv420sp[uvp++]) - 128;
					u = (0xff & yuv420sp[uvp++]) - 128;
				}

				y1192 = 1192 * y;
				r = (y1192 + 1634 * v);
				g = (y1192 - 833 * v - 400 * u);
				b = (y1192 + 2066 * u);

				r = r < 0 ? 0 : r < 262143 ? r : 262143;
				g = r < 0 ? 0 : g < 262143 ? g : 262143;
				b = r < 0 ? 0 : b < 262143 ? b : 262143;

				rgb[yp] = ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00)
						| ((b >> 10) & 0xff);
			}
		}
	}

	private void RGBtoHSV(float r, float g, float b, float[] hsv) {
		float min, max, delta;
		min = Math.min(r, Math.min(g, b));
		max = Math.max(r, Math.max(g, b));
		hsv[2] = max;
		delta = max - min;
		if (max != 0)
			hsv[1] = delta / max;
		else {
			hsv[1] = 0;
			hsv[0] = -1;
			return;
		}
		if (r == max)
			hsv[0] = (g - b) / delta;
		else if (g == max)
			hsv[0] = 2 + (b - r) / delta;
		else
			hsv[0] = 4 + (r - g) / delta;
		hsv[0] *= 60;
		if (hsv[0] < 0)
			hsv[0] += 360;
	}

	/**
	 * Butterworth Bandpass filter
	 * http://www-users.cs.york.ac.uk/~fisher/cgi-bin/mkfscript
	 */
	private float[] butterworthBandpassFilter(float[] inputData) {
		final int NZEROS = 8;
		final int NPOLES = 8;
		double[] xv = new double[NZEROS + 1];
		double[] yv = new double[NPOLES + 1];
		double dGain = 1.232232910e+02;

		float[] outputData = new float[inputData.length];
		for (int i = 0; i < outputData.length; i++) {
			double input = inputData[i];
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

			outputData[i] = (float) yv[8];
		}
		return outputData;
	}

	private int medianPeak(float[] inputData) {
		List<Integer> peakList = new ArrayList<Integer>();
		int count = 4;
		for (int i = 3; i < inputData.length - 3; i++, count++) {
			if (inputData[i] > 0 && inputData[i] > inputData[i - 1]
					&& inputData[i] > inputData[i - 2]
					&& inputData[i] > inputData[i - 3]
					&& inputData[i] >= inputData[i + 1]
					&& inputData[i] >= inputData[i + 2]
					&& inputData[i] >= inputData[i + 3]) {
				peakList.add(count);
				i += 3;
				count = 3;
			}
		}
		peakList.set(0, peakList.get(0) + count + 2);		
		Collections.sort(peakList);
		return peakList.get(peakList.size() * 2 / 3);
	}

	private float[] medianSmoothing(float[] inputData) {
		float[] newData = new float[inputData.length];

		for (int i = 0; i < inputData.length; i++) {
			if (i == 0 || i == 1 || i == 2 || i == inputData.length - 1
					|| i == inputData.length - 2 || i == inputData.length - 3) {
				newData[i] = inputData[i];
			} else {
				float[] items = { inputData[i - 2], inputData[i - 1],
						inputData[i], inputData[i + 1], inputData[i + 2] };
				Arrays.sort(items);
				newData[i] = items[2];
			}
		}

		return newData;
	}

}
