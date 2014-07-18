package com.yadli.luminara;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;

import com.stericson.RootTools.*;
import com.stericson.RootTools.exceptions.RootDeniedException;
import com.stericson.RootTools.execution.Command;
import com.stericson.RootTools.execution.CommandCapture;
import com.stericson.RootTools.execution.Shell;

import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.os.Build;

public class MainActivity extends ActionBarActivity {

	static final int LOOP_PERIOD = 20;
	static final float minimalMax = 100.0f;
	static final float decayStrength = 0.99f;

	static boolean applicationStarted = false;
	static Activity currentActivity = null;

	static boolean started = false;
	static boolean onPowerSave = false;
	static Visualizer visualizer = null;

	static float loMax, miMax, hiMax;
	static float rmsMax = 1.0f;
	static final float rmsMinMax = 1.0f;
	static float intensityMin = 0.5f;
	static float intensityMax = 0.51f;
	static float intensityHistory = 0.0f;

	static int loopCount = 0;
	static int triggerCount = 0;
	static float threshold = 2.0f;
	static int triggerPos = 0;
	static int Fs = -1;
	static int N = -1;
	static int captureRate = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// INITIALIZE RECEIVER
		if (!applicationStarted) {
			currentActivity = this;
			IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
			filter.addAction(Intent.ACTION_SCREEN_OFF);
			BroadcastReceiver mReceiver = new ScreenReceiver();
			registerReceiver(mReceiver, filter);
		}

		applicationStarted = true;

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.donate) {
			donate();
		}
		return super.onOptionsItemSelected(item);
	}

	private void donate() {
		Intent goToMarket = new Intent(Intent.ACTION_VIEW).setData(Uri
				.parse("market://details?id=com.yadli.luminaradonationkey"));
		startActivity(goToMarket);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void onDestroy() {
		Log.d("visualizer", "onDestroy");
		stop();
		super.onDestroy();
	}

	@Override
	public void onStop() {
		super.onStop();
	}

	@Override
	public void onBackPressed() {
		// this prevents the Activity to be killed
		// http://sr1.me/way-to-explore/2013/09/14/make-your-android-application-run-at-background.html
		moveTaskToBack(false);
	}

	static int peak = 0;

	private static void OnWaveData(byte[] bytes) {
		peak = 0;
		for (byte b : bytes) {
			int val = Math.abs(b);
			if (val > peak)
				peak = val;
		}
		Log.d("visualizer", "peak = " + peak);
	}

	private static int boost(int val) {
		val *= 1.2;
		if (val > 255)
			val = 255;
		return val;
	}

	private static void onFFTData(byte[] bytes, int samplingRate) {
		audioEngine_v3(bytes);
		periodicalCheck();
	}

	protected static void adaptivaeThresholdCalc() {
		// Log.d("visualizer", "t=" + triggerCount);
		if (triggerCount > 3) {
			threshold /= 0.9;
			// Log.d("visualizer", "threshold up to " + threshold);
		}
		if (triggerCount < 2) {
			threshold *= 0.9;
			// Log.d("visualizer", "threshold down to " + threshold);
		}

		if (threshold < 1.5f)
			threshold = 1.5f;
	}

	protected static void periodicalCheck() {
		++loopCount;
		if (loopCount == LOOP_PERIOD) {

			adaptivaeThresholdCalc();

			// reset loopCount
			loopCount = 0;
			triggerCount = 0;

			powerSaveIfOnLazyCommit();
		}
	}

	static int[] bark_bands = new int[] { 100, 200, 300, 400, 510, 630, 770,
			920, 1080, 1270, 1480, 1720, 2000, 2320, 2700, 3150, 3700, 4400,
			5300, 6400, 7700, 9500, 12000, 15500 };
	static int[] bark_bands_table = null;
	static float[] amplitudes = new float[24];
	static float[] rgb = new float[3];

	static float current_hue_percent = 0;
	static SpectrumView spectrumView = null;

	protected static void audioEngine_v3(byte[] bytes) {

		int len = bytes.length;

		if (bark_bands_table == null) {
			bark_bands_table = new int[bytes.length];
			int bIndex = 0;
			for (int i = 0; i < len; i += 2) {
				int freq = (i / 2 * Fs) / (N);
				if (freq > bark_bands[bIndex] && bIndex < 23) {
					Log.d("barkband", "freq = " + freq + ", b= " + bIndex
							+ "i=" + i);
					bIndex++;
				}
				bark_bands_table[i] = bIndex;
			}
		}

		int index = 2;// 0 and 1 are real
						// part of F0 and
						// Fn/2

		int lowBound = 20;

		for (int i = 0; i < 24; ++i)
			amplitudes[i] = 0.f;

		for (; index < len; index += 2) {
			float re = bytes[index] / 255.f;
			float im = bytes[index + 1] / 255.f;
			float val = (float) (re * re) + (float) (im * im);

			amplitudes[bark_bands_table[index]] += val;
		}

		for (int i = 0; i < 24; ++i)
			amplitudes[i] = (float) Math.sqrt(amplitudes[i]) * 4;

		float intensity = calculateIntensity(amplitudes[0]);

		// amplitudes[23] = 0;

		float maxVal = -1;
		int maxIndex = 0;

		for (int i = 0; i < 12; ++i)
			if (amplitudes[i] > maxVal) {
				maxVal = amplitudes[i];
				maxIndex = i;
			}
		float percent = (float) maxIndex / 12.0f;
		current_hue_percent = current_hue_percent * 0.85f + percent * 0.15f;
		calculate_rgb(current_hue_percent);

		if (spectrumView != null) {
			spectrumView.setPivot(current_hue_percent, r, g, b, intensity);
			spectrumView.update(amplitudes);
		}

		int loVal = (int) (r * intensity);
		int miVal = (int) (g * intensity);
		int hiVal = (int) (b * intensity);
		loVal = boost(loVal);
		miVal = boost(miVal);
		hiVal = boost(hiVal);
		LEDOperator.commit(loVal, miVal, hiVal);
	}

	protected static float calculateIntensity(float rms) {
		if (rms > rmsMax)
			rmsMax = rms;
		else {
			rmsMax *= decayStrength;
			if (rmsMax < rmsMinMax)
				rmsMax = rmsMinMax;
		}

		float intensity = rms / rmsMax;
		// normalize intensity

		if (intensity < intensityMin)
			intensityMin = intensity;
		else {
			intensityMin *= 1.1;
			if (intensityMin < 0.1f)
				intensityMin = 0.1f;
			// intensityMin = (intensity + intensityMin) / 2;
		}

		if (intensity > intensityMax)
			intensityMax = intensity;
		else
			intensityMax *= decayStrength;
		// intensityMax = (intensity + intensityMax) / 2;

		if (intensityMin > intensityMax) {
			if (intensityMin > 0.5)
				intensityMin *= decayStrength;
			else
				intensityMin /= decayStrength;
			intensityMax = intensityMin + 0.01f;
		}
		float range = intensityMax - intensityMin;

		intensity = (intensity - intensityMin) / range;
		if (intensity < 0)
			intensity = 0;
		if (intensity > 1)
			intensity = 1;

		if (intensity < intensityHistory * threshold) {
			intensity = (float) (intensity * 0.1 + intensityHistory * 0.9);
			intensity *= 0.7;
		} else {
			intensity = 1;
			++triggerCount;
			if (loopCount - triggerPos > 3) {
				triggerPos = loopCount;
				// XXX something's wrong
			}
		}
		intensityHistory = intensity;

		return intensity;
	}

	static float[] history_fft = null;
	static float[] history_weight = null;
	static int current_follow_idx = -1;
	static float current_follow_value = 0;
	static float strength = 0;
	static int r = 0, g = 0, b = 0;

	protected static void calculate_rgb(float percent) {
		percent *= 2;
		if (percent > 1)
			percent = 1;
		int hue = (int) (percent * 300);// limit hue to 0~300

		if (hue < 60) {
			b = 0;
			r = 255;
			g = hue * 255 / 60;
		} else if (hue < 120) {
			b = 0;
			r = 255 - (hue - 60) * 255 / 60;
			g = 255;
		} else if (hue < 180) {
			r = 0;
			g = 255;
			b = (hue - 120) * 255 / 60;
		} else if (hue < 240) {
			r = 0;
			b = 255;
			g = 255 - (hue - 180) * 255 / 60;
		} else {
			b = 255;
			g = 0;
			r = (hue - 240) * 255 / 60;
		}
	}

	//
	// protected static void audioEngine_v2(byte[] bytes) {
	// int len = bytes.length;
	// if (history_fft == null)
	// history_fft = new float[len];
	// if (history_weight == null)
	// history_weight = new float[len];
	// int index = 2;
	// boolean picking = false;
	// strength *= 0.4f;
	// if (current_follow_idx != -1)
	// history_weight[current_follow_idx] *= 0.8f;
	//
	// if (strength < 0.1f)
	// current_follow_idx = -1;
	//
	// if (current_follow_idx != -1) {
	// int re = bytes[current_follow_idx];
	// int im = bytes[current_follow_idx + 1];
	// float val = (float) Math.sqrt(re * re + im * im);
	// strength = val / current_follow_value * 0.75f;
	// if (strength < 0.9f) {
	// Log.d("visualizer", "follow end");
	// current_follow_idx = -1;
	// }
	// }
	//
	// for (; index < len; index += 2) {
	// int re = bytes[index];
	// int im = bytes[index + 1];
	// float val = (float) Math.sqrt(re * re + im * im);
	//
	// if (index != current_follow_idx) {
	//
	// float threshold = 6.0f - (index * 12.0f / len);
	//
	// if (val > 20) {// it should be at least this high.
	// if (val > history_fft[index] * threshold) {
	// boolean change = !picking;
	//
	// if (picking
	// && history_weight[index] < history_weight[current_follow_idx]) {
	// history_weight[index] = 1;
	// change = true;
	// }
	//
	// if (!picking && current_follow_idx != -1) {
	// if (val / history_fft[index] < strength)
	// change = false;
	// }
	//
	// if (change) {
	// Log.d("visualizer", "follow start on " + index
	// + ", val= " + val + " threshold="
	// + threshold);
	// current_follow_idx = index;
	// current_follow_value = val;
	// strength = 0.75f;
	// float percent = (float) current_follow_idx
	// / (float) history_fft.length;
	// calculate_rgb(percent);
	// picking = true;
	// }
	// }
	// }
	// }
	//
	// history_fft[index] = val;
	// }
	//
	// if (strength > 1)
	// strength = 1;
	// if (strength < 0)
	// strength = 0;
	//
	// commit((int) (strength * r), (int) (strength * g), (int) (strength * b));
	// }

	public static void resumeIfOnPowerSave() {
		if (onPowerSave) {
			onPowerSave = false;
			start();
		}
	}

	public static void powerSaveIfOnLazyCommit() {
		if (ScreenReceiver.screenIsOn)
			return;
		if (LEDOperator.onLazyCommit) {
			// we're going down, we're going
			Log.d("visualizer", "entering power save mode");
			onPowerSave = true;
			stop();
		} else {
			// Log.d("visualizer", "active, not going power save");
		}
	}

	public static boolean start() {
		if (LEDOperator.initialize(currentActivity)) {
			startVisualizer();
			return true;
		}
		return false;
	}

	public static void stop() {

		if (visualizer != null) {
			visualizer.setEnabled(false);
			visualizer.release();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			LEDOperator.stop();
		}
		visualizer = null;
		started = false;

	}

	protected static void startVisualizer() {
		try {

			visualizer = new Visualizer(0);
			int[] ranges = Visualizer.getCaptureSizeRange();
			int idx = -1;
			for (int i = 0; i < ranges.length; ++i)
				if (ranges[i] == 1024)
					idx = i;
			if (idx == -1)
				idx = 1;
			visualizer.setCaptureSize(ranges[idx]);
			captureRate = visualizer.getMaxCaptureRate() / 1000;
			// visualizer.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);
			hiMax = miMax = loMax = minimalMax;
			Fs = visualizer.getSamplingRate() / 1000;
			N = visualizer.getCaptureSize();
			Log.d("visualizer", "Sampling rate = " + Fs);
			Log.d("visualizer", "Capture size = " + N);
			Log.d("visualizer", "Capture rate = " + captureRate);
			visualizer.setDataCaptureListener(
					new Visualizer.OnDataCaptureListener() {
						public void onWaveFormDataCapture(
								Visualizer visualizer, byte[] bytes,
								int samplingRate) {
							OnWaveData(bytes);
						}

						public void onFftDataCapture(Visualizer visualizer,
								byte[] bytes, int samplingRate) {
							onFFTData(bytes, samplingRate);
						}

					}, Visualizer.getMaxCaptureRate(), false, true);

			visualizer.setEnabled(true);
			started = true;
		} catch (Exception e) {
			AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(
					currentActivity);
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			pw.close();
			// dlgBuilder.setMessage(e.toString()+"\n"+e.);
			dlgBuilder.setMessage(sw.toString());
			dlgBuilder.setTitle("Alert");
			dlgBuilder.show();
			e.printStackTrace();
		}
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container,
					false);
			final Button b = (Button) rootView.findViewById(R.id.button1);
			spectrumView = (SpectrumView) rootView
					.findViewById(R.id.spectrumView1);

			if (started) {// if the view is recreated, we set text to stop
				b.setText("STOP");
			}

			b.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					if (!started) {
						// http://stackoverflow.com/questions/5293615/how-can-i-get-root-permissions-through-the-android-sdk
						// IS WRONG

						if (start()) {
							if (started)
								b.setText("STOP");
						} else {
							AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(
									getActivity());
							dlgBuilder.setMessage("Failed to get ROOT access.");
							dlgBuilder.setTitle("Alert");
							dlgBuilder.show();
						}

					} else {

						stop();

						b.setText("START");
					}

				}

			});

			return rootView;
		}

	}

}
