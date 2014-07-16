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
	static final int LAZY_LATCH_THRESHOLD = 1000;
	static final float minimalMax = 100.0f;
	static final float decayStrength = 0.99f;

	static boolean applicationStarted = false;
	static Activity currentActivity = null;

	static boolean started = false;
	static boolean onLazyCommit = false;
	static boolean onFatalError = false;
	static int lazyLatch = 0;
	static int commit_clk = 0;
	static boolean onPowerSave = false;
	static Visualizer visualizer = null;
	static private File dir;
	static private HashMap<String, File> leds;

	static float loMax, miMax, hiMax;
	static float rms = 0.0f;
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
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void onDestroy() {
		commit(0, 0, 0);
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

	private static void onFFTData(byte[] bytes) {
		audioEngine_v3(bytes);
		periodicalCheck();
	}

	protected static void adaptivaeThresholdCalc() {
		Log.d("visualizer", "t=" + triggerCount);
		if (triggerCount > 3) {
			threshold /= 0.9;
			Log.d("visualizer", "threshold up to " + threshold);
		}
		if (triggerCount < 2) {
			threshold *= 0.9;
			Log.d("visualizer", "threshold down to " + threshold);
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

		rms = 0.0f;
		for (int i = 0; i < 24; ++i)
			amplitudes[i] = 0.f;

		for (; index < len; index += 2) {
			int re = bytes[index];
			int im = bytes[index + 1];
			float val = (float) Math.sqrt(re * re + im * im);

			if (index <= lowBound)
				rms += val;

			amplitudes[bark_bands_table[index]] += val / 360.0f;
		}

		// {{{ Intensity
		// adaptive scaling

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
		// }}}

		rgb[0] = rgb[1] = rgb[2] = 0.f;
		for (int i = 0; i < 24; ++i)
			rgb[i / 8] += amplitudes[i] * amplitudes[i];

		rgb[0] = (float) Math.sqrt(rgb[0]);
		rgb[1] = (float) Math.sqrt(rgb[1]);
		rgb[2] = (float) Math.sqrt(rgb[2]);

		float maxVal = -1;
		int maxIndex = 0;

		for (int i = 0; i < 12; ++i)
			if (amplitudes[i] > maxVal) {
				maxVal = amplitudes[i];
				maxIndex = i;
			}
		float percent = (float) maxIndex / 12.0f;
		current_hue_percent = current_hue_percent * 0.85f + percent * 0.15f;
		Log.d("percent", "percent="+current_hue_percent);
		calculate_rgb(current_hue_percent);

		 int loVal = (int) (r * intensity);
		 int miVal = (int) (g * intensity);
		 int hiVal = (int) (b * intensity);
		 loVal = boost(loVal);
		 miVal = boost(miVal);
		 hiVal = boost(hiVal);

		// if(rgb[0] > loMax)
		// loMax = rgb[0];
		// if(rgb[1] > miMax)
		// miMax = rgb[1];
		// if(rgb[2] > hiMax)
		// hiMax = rgb[2];
		// if(hiMax < 50)
		// hiMax = 50;
		// Log.d("visualizer", "r=" + rgb[0] + " g=" + rgb[1] + " b=" + rgb[2]);

		// int loVal = (int) (rgb[0] * 255 / loMax * intensity);
		// int miVal = (int) (rgb[1] * 255 / miMax * intensity);
		// int hiVal = (int) (rgb[2] * 255 / hiMax * intensity);

		// int loVal = (int) (rgb[0] * 255 * intensity);
		// int miVal = (int) (rgb[1] * 255 * intensity);
		// int hiVal = (int) (rgb[2] * 255 * intensity);
		//
		// loVal = boost(loVal);

		// miVal = boost(miVal);
		// hiVal = boost(hiVal);
		// Log.d("visualizer", "lo = " + loVal + " mid=" + miVal + " hi="
		// + hiVal + "lm=" + loMax + " mm=" + miMax + " hm=" + hiMax);

		// Log.d("visualizer", "rms = \t"+rms + "\tmax = \t"+rmsMax +
		// "\tintensity=\t"+intensity);
		// Log.d("visualizer", "peak = \t"+peak + "\tmax = \t"+peakMax +
		// "\tintensity=\t"+intensity);

		// Time to set brightness. Don't go
		// crazy!

		// LEDOperator op = new LEDOperator();
		// op.execute(new int[] { loVal, miVal,
		// hiVal });

		// int loVal = (int) (rgb[0] * 255 / loMax);
		// int miVal = (int) (rgb[1] * 255 / miMax);
		// int hiVal = (int) (rgb[2] * 255 / hiMax);

		commit(loVal, miVal, hiVal);
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

	protected static void audioEngine_v2(byte[] bytes) {
		int len = bytes.length;
		if (history_fft == null)
			history_fft = new float[len];
		if (history_weight == null)
			history_weight = new float[len];
		int index = 2;
		boolean picking = false;
		strength *= 0.4f;
		if (current_follow_idx != -1)
			history_weight[current_follow_idx] *= 0.8f;

		if (strength < 0.1f)
			current_follow_idx = -1;

		if (current_follow_idx != -1) {
			int re = bytes[current_follow_idx];
			int im = bytes[current_follow_idx + 1];
			float val = (float) Math.sqrt(re * re + im * im);
			strength = val / current_follow_value * 0.75f;
			if (strength < 0.9f) {
				Log.d("visualizer", "follow end");
				current_follow_idx = -1;
			}
		}

		for (; index < len; index += 2) {
			int re = bytes[index];
			int im = bytes[index + 1];
			float val = (float) Math.sqrt(re * re + im * im);

			if (index != current_follow_idx) {

				float threshold = 6.0f - (index * 12.0f / len);

				if (val > 20) {// it should be at least this high.
					if (val > history_fft[index] * threshold) {
						boolean change = !picking;

						if (picking
								&& history_weight[index] < history_weight[current_follow_idx]) {
							history_weight[index] = 1;
							change = true;
						}

						if (!picking && current_follow_idx != -1) {
							if (val / history_fft[index] < strength)
								change = false;
						}

						if (change) {
							Log.d("visualizer", "follow start on " + index
									+ ", val= " + val + " threshold="
									+ threshold);
							current_follow_idx = index;
							current_follow_value = val;
							strength = 0.75f;
							float percent = (float) current_follow_idx
									/ (float) history_fft.length;
							calculate_rgb(percent);
							picking = true;
						}
					}
				}
			}

			history_fft[index] = val;
		}

		if (strength > 1)
			strength = 1;
		if (strength < 0)
			strength = 0;

		commit((int) (strength * r), (int) (strength * g), (int) (strength * b));
	}

	protected static void audioEngine_v1(byte[] bytes) {
		int len = bytes.length;
		int index = 2;// 0 and 1 are real
						// part of F0 and
						// Fn/2

		int low = 0, mid = 0, high = 0;
		int lowBound = 20;
		int midBound = 100;// 20k / 20 = 1Khz
							// as midbound

		rms = 0.0f;

		for (; index < len; index += 2) {
			int re = bytes[index];
			int im = bytes[index + 1];
			int val = (int) Math.sqrt(re * re + im * im);

			if (index <= lowBound)
				rms += val;

			if (index <= lowBound) {
				low += val;
			} else if (index <= midBound) {
				mid += val;
			} else {
				high += val;
			}
		}

		// adaptive scaling

		if (low > loMax)
			loMax = low;
		else {
			loMax *= decayStrength;
			if (loMax < minimalMax) {
				loMax = minimalMax;
			}
		}
		if (mid > miMax)
			miMax = mid;
		else {
			miMax *= decayStrength;
			if (miMax < minimalMax)
				miMax = minimalMax;
		}
		if (high > hiMax)
			hiMax = high;
		else {
			hiMax *= decayStrength;
			if (hiMax < minimalMax)
				hiMax = minimalMax;
		}

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

		// intensity = (float) Math.sqrt(intensity);
		// intensity = (float) Math.sqrt(intensity);

		// if (intensity > intensityMax * 0.6)
		// Log.d("visualizer", "Trigger");

		// Log.d("visualizer", "range = \t" + range + "\tmax = \t"
		// + intensityMax + "\tmin=\t" + intensityMin + "\tval=\t"
		// + intensity);

		// float intensity = peak / peakMax;
		// intensity *= intensity * intensity * intensity * intensity;

		// int loVal = (int) (255 * intensity);
		// int miVal = (int) (255 * intensity);
		// int hiVal = (int) (255 * intensity);

		int loVal = (int) (low * 255 / loMax * intensity);
		int miVal = (int) (mid * 255 / miMax * intensity);
		int hiVal = (int) (high * 255 / hiMax * intensity);

		// loVal = boost(loVal);
		miVal = boost(miVal);
		hiVal = boost(hiVal);
		// Log.d("visualizer", "lo = " + loVal + " mid=" + miVal + " hi="
		// + hiVal + "lm=" + loMax + " mm=" + miMax + " hm=" + hiMax);

		// Log.d("visualizer", "rms = \t"+rms + "\tmax = \t"+rmsMax +
		// "\tintensity=\t"+intensity);
		// Log.d("visualizer", "peak = \t"+peak + "\tmax = \t"+peakMax +
		// "\tintensity=\t"+intensity);

		// Time to set brightness. Don't go
		// crazy!

		// LEDOperator op = new LEDOperator();
		// op.execute(new int[] { loVal, miVal,
		// hiVal });

		commit(loVal, miVal, hiVal);
	}

	public static void resumeIfOnPowerSave() {
		if (onPowerSave) {
			onPowerSave = false;
			startVisualizer();
		}
	}

	public static void powerSaveIfOnLazyCommit() {
		if (ScreenReceiver.screenIsOn)
			return;
		if (onLazyCommit) {
			// we're going down, we're going
			Log.d("visualizer", "entering power save mode");
			onPowerSave = true;
			stop();
		} else {
			Log.d("visualizer", "active, not going power save");
		}
	}

	public static void stop() {

		if (visualizer != null) {
			visualizer.setEnabled(false);
			visualizer.release();
			commit(0, 0, 0);
		}
		visualizer = null;
		started = false;

	}

	public static void commit(int loVal, int miVal, int hiVal) {
		try {
			boolean needCommit = true;

			if (loVal == 0 && miVal == 0 && hiVal == 0) {
				if (onLazyCommit)
					needCommit = false;
				else {
					++lazyLatch;

					if (lazyLatch > LAZY_LATCH_THRESHOLD) {
						Log.d("visualizer", "entering lazy commit mode");
						onLazyCommit = true;
					}
				}
			} else {
				onLazyCommit = false;
				lazyLatch = 0;
			}

			if (needCommit && !onFatalError) {
				postValueToFile(loVal, "red");
				postValueToFile(miVal, "green");
				postValueToFile(hiVal, "blue");

				// LG G2 back LED support

				postValueToFile(loVal, "back-red");
				postValueToFile(miVal, "back-green");
				postValueToFile(hiVal, "back-blue");
				
				// Xperia T series

				postValueToFile((loVal + miVal) / 2, "logo1");
				postValueToFile((hiVal + miVal) / 2, "logo2");
				postValueToFile((hiVal + miVal + loVal) / 3, "button");

				// Oppo Skyline
				
				// postValueToFile((hiVal + miVal + loVal) / 3, "kpdbl-lut-2");
				// postValueToFile((hiVal + miVal + loVal) / 3, "kpdbl-pwm-3");
				postValueToFile((hiVal + miVal + loVal) / 3, "kpdbl-lut-4");//not working
				
				// Xperia SP
				postValueToFile(loVal, "SP-R1");
				postValueToFile(miVal, "SP-G1");
				postValueToFile(hiVal, "SP-B1");

				postValueToFile(loVal, "SP-R2");
				postValueToFile(miVal, "SP-G2");
				postValueToFile(hiVal, "SP-B2");

				postValueToFile(loVal, "SP-R3");
				postValueToFile(miVal, "SP-G3");
				postValueToFile(hiVal, "SP-B3");
				// Nexus N5 fix
				postValueToFile(50, 0, "on-off-red");
				postValueToFile(50, 0, "on-off-green");
				postValueToFile(50, 0, "on-off-blue");

				postValueToFile(commit_clk++, "trigger");
			}

		} catch (Exception e) {
			onFatalError = true;
			AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(
					currentActivity);
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			pw.close();
			dlgBuilder.setTitle("Failed to write to led interface.");
			dlgBuilder.setMessage(sw.toString());
			dlgBuilder.show();
			e.printStackTrace();
			stop();
		}

	}

	public static void postValueToFile(int val, String key) throws IOException {
		File f;
		FileOutputStream os;
		if (leds != null && leds.containsKey(key)) {
			f = leds.get(key);
			os = new FileOutputStream(f);
			os.write(Integer.toString(val).getBytes());
			os.close();
		}
	}

	public static void postValueToFile(int val1, int val2, String key)
			throws IOException {
		File f;
		FileOutputStream os;
		if (leds != null && leds.containsKey(key)) {
			f = leds.get(key);
			os = new FileOutputStream(f);
			os.write((Integer.toString(val1) + " " + Integer.toString(val2))
					.getBytes());
			os.close();
		}
	}

	protected static void startVisualizer() {
		try {

			visualizer = new Visualizer(0);
			int[] ranges = Visualizer.getCaptureSizeRange();
			int idx = -1;
			for (int i = 0; i < ranges.length; ++i)
				if (ranges[i] == 256)
					idx = i;
			if (idx == -1)
				idx = 1;
			visualizer.setCaptureSize(ranges[idx]);
			hiMax = miMax = loMax = minimalMax;
			Fs = visualizer.getSamplingRate() / 1000;
			N = visualizer.getCaptureSize();
			Log.d("visualizer", "Sampling rate = " + Fs);
			Log.d("visualizer", "Capture size = " + N);
			visualizer.setDataCaptureListener(
					new Visualizer.OnDataCaptureListener() {
						public void onWaveFormDataCapture(
								Visualizer visualizer, byte[] bytes,
								int samplingRate) {
							OnWaveData(bytes);
						}

						public void onFftDataCapture(Visualizer visualizer,
								byte[] bytes, int samplingRate) {
							onFFTData(bytes);
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

		protected CommandCapture hackBrightnessFile(String path, File target) {
			// AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(
			// getActivity());
			// dlgBuilder.setMessage("putting "+path+" to "+
			// target.getAbsolutePath());
			// dlgBuilder.setTitle("Debug");
			// dlgBuilder.show();
			return new CommandCapture(0, "chmod 666 " + path,
					"chown root:sdcard_r " + path,

					"rm -f" + target.getAbsolutePath(),

					"ln -s " + path + " " + target.getAbsolutePath());
		}

		protected void probe_rgb() {
			File f;
			f = new File("/sys/class/leds/led:rgb_red/brightness");
			if (f.exists())
				leds.put("red", f);
			f = new File("/sys/class/leds/pwr-red/brightness");
			if (f.exists())
				leds.put("red", f);
			f = new File("/sys/class/leds/red/brightness");
			if (f.exists())
				leds.put("red", f);
			f = new File("/sys/class/leds/led_r/brightness");
			if (f.exists())
				leds.put("red", f);

			f = new File("/sys/class/leds/led:rgb_blue/brightness");
			if (f.exists())
				leds.put("blue", f);
			f = new File("/sys/class/leds/pwr-blue/brightness");
			if (f.exists())
				leds.put("blue", f);
			f = new File("/sys/class/leds/blue/brightness");
			if (f.exists())
				leds.put("blue", f);
			f = new File("/sys/class/leds/led_b/brightness");
			if (f.exists())
				leds.put("blue", f);

			f = new File("/sys/class/leds/led:rgb_green/brightness");
			if (f.exists())
				leds.put("green", f);
			f = new File("/sys/class/leds/pwr-green/brightness");
			if (f.exists())
				leds.put("green", f);
			f = new File("/sys/class/leds/green/brightness");
			if (f.exists())
				leds.put("green", f);
			f = new File("/sys/class/leds/led_g/brightness");
			if (f.exists())
				leds.put("green", f);
		}

		protected void probe_logo() {
			File f;
			f = new File("/sys/class/leds/logo-backlight_1/brightness");
			if (f.exists())
				leds.put("logo1", f);

			f = new File("/sys/class/leds/logo-backlight_2/brightness");
			if (f.exists())
				leds.put("logo2", f);
		}

		protected void probe_special() {
			File f;

			// TODO detect LT26 device name
			f = new File("/sys/class/leds/button-backlight/brightness");
			if (f.exists())
				leds.put("button", f);

			f = new File("/sys/class/leds/kpdbl-lut-2/brightness");
			if (f.exists())
				leds.put("kpdbl-lut-2", f);

			f = new File("/sys/class/leds/kpdbl-pwm-3/brightness");
			if (f.exists())
				leds.put("kpdbl-pwm-3", f);

			f = new File("/sys/class/leds/kpdbl-pwm-4/brightness");
			if (f.exists())
				leds.put("kpdbl-pwm-4", f);

			f = new File("/sys/class/leds/R/brightness");
			if (f.exists())
				leds.put("back-red", f);
			f = new File("/sys/class/leds/G/brightness");
			if (f.exists())
				leds.put("back-green", f);
			f = new File("/sys/class/leds/B/brightness");
			if (f.exists())
				leds.put("back-blue", f);

//			LED1_R
//			LED1_G
//			LED1_B
//			LED2_R
//			LED2_G
//			LED2_B
//			LED3_R
//			LED3_G
//			LED3_B
			f = new File("/sys/class/leds/LED1_R/brightness");
			if (f.exists())
				leds.put("SP-R1", f);
			f = new File("/sys/class/leds/LED2_R/brightness");
			if (f.exists())
				leds.put("SP-R2", f);
			f = new File("/sys/class/leds/LED3_R/brightness");
			if (f.exists())
				leds.put("SP-R3", f);

			f = new File("/sys/class/leds/LED1_G/brightness");
			if (f.exists())
				leds.put("SP-G1", f);
			f = new File("/sys/class/leds/LED2_G/brightness");
			if (f.exists())
				leds.put("SP-G2", f);
			f = new File("/sys/class/leds/LED3_G/brightness");
			if (f.exists())
				leds.put("SP-G3", f);

			f = new File("/sys/class/leds/LED1_B/brightness");
			if (f.exists())
				leds.put("SP-B1", f);
			f = new File("/sys/class/leds/LED2_B/brightness");
			if (f.exists())
				leds.put("SP-B2", f);
			f = new File("/sys/class/leds/LED3_B/brightness");
			if (f.exists())
				leds.put("SP-B3", f);
		}

		protected void probe_trigger() {
			File f;

			f = new File("/sys/class/leds/red/on_off_ms");
			if (f.exists())
				leds.put("on-off-red", f);
			f = new File("/sys/class/leds/green/on_off_ms");
			if (f.exists())
				leds.put("on-off-green", f);
			f = new File("/sys/class/leds/blue/on_off_ms");
			if (f.exists())
				leds.put("on-off-blue", f);

			f = new File("/sys/class/leds/red/rgb_start");
			if (f.exists())
				leds.put("trigger", f);
			// f = new File("/sys/class/leds/green/rgb_start");
			// if (f.exists())
			// leds.put("trigger-green", f);
			// f = new File("/sys/class/leds/blue/rgb_start");
			// if (f.exists())
			// leds.put("trigger-blue", f);
		}

		protected void finalCheckBeforeStart() {
			// boolean fileNotFound = false;
			// boolean ioException = false;
			// try {
			// postValueToFile(0, "red");
			// } catch (FileNotFoundException e) {
			// fileNotFound = true;
			// } catch (IOException e) {
			// ioException = true;
			// }
			// XXX problem with this
			// if (fileNotFound)// try to fix this problem
			// {
			// String filePath = dir.getAbsolutePath();
			// if (RootTools.isAccessGiven()) {
			//
			// final StringBuilder userNameStrBuilder = new StringBuilder();
			//
			// Command unameCmd = new Command(0, "ls -la " + filePath
			// + "/../") {
			//
			// @Override
			// public void commandCompleted(int arg0, int arg1) {
			// // TODO Auto-generated method stub
			//
			// }
			//
			// @Override
			// public void commandOutput(int arg0, String arg1) {
			// if (arg1.contains("files"))// that's it.
			// {
			// String[] strs = arg1.split(" ");
			// Log.d("visualizer", "user name = " + strs[1]);
			// userNameStrBuilder.append(strs[1]);
			// }
			// }
			//
			// @Override
			// public void commandTerminated(int arg0, String arg1) {
			// // TODO Auto-generated method stub
			//
			// }
			//
			// };
			// try {
			// Shell sh = RootTools.getShell(true);
			// sh.add(unameCmd);
			// String uname = userNameStrBuilder.toString();
			// if (!uname.equals("")) {
			// for (String name : leds.keySet()) {
			// Command chownCmd = new Command(0, "chown "
			// + uname + ":" + uname + " "
			// + leds.get(name).getAbsolutePath()) {
			//
			// @Override
			// public void commandCompleted(int arg0,
			// int arg1) {
			// // TODO Auto-generated method stub
			//
			// }
			//
			// @Override
			// public void commandOutput(int arg0,
			// String arg1) {
			// Log.d("visualizer", arg1);
			// }
			//
			// @Override
			// public void commandTerminated(int arg0,
			// String arg1) {
			// // TODO Auto-generated method stub
			//
			// Log.d("visualizer", arg1);
			// }
			//
			// };
			// sh.add(chownCmd);
			// }
			// }
			// } catch (IOException e) {
			// // TODO Auto-generated catch block
			// e.printStackTrace();
			// } catch (TimeoutException e) {
			// // TODO Auto-generated catch block
			// e.printStackTrace();
			// } catch (RootDeniedException e) {
			// // TODO Auto-generated catch block
			// e.printStackTrace();
			// }
			// }
			// }

		}

		protected boolean initialize() {

			leds = new HashMap<String, File>();

			try {
				dir = getActivity().getFilesDir();

				probe_rgb();
				probe_logo();// for Xperia TX
				probe_special(); // for my beloved Xperia SL
				probe_trigger(); // for Nexus 5

				if (RootTools.isAccessGiven()) {

					Shell sh = RootTools.getShell(true);

					CommandCapture rmCmd = new CommandCapture(0, "rm -f "
							+ dir.getAbsolutePath() + "/*");
					sh.add(rmCmd);

					for (String key : leds.keySet()) {
						File f = new File(dir, key);
						CommandCapture cmd = hackBrightnessFile(leds.get(key)
								.getAbsolutePath(), f);
						sh.add(cmd);
						leds.put(key, f);
					}
					
					while(sh.isExecuting)
					{
						Thread.sleep(1);
					}
					
					return true;
				}

				// Runtime.getRuntime()
				// .exec("su -c '"
				// + "'");
			} catch (Exception e) {
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				pw.close();
				AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(
						getActivity());
				// dlgBuilder.setMessage(e.toString()+"\n"+e.);
				dlgBuilder.setMessage(sw.toString());
				dlgBuilder.setTitle("Alert");
				dlgBuilder.show();

				e.printStackTrace();
			}
			return false;
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container,
					false);
			final Button b = (Button) rootView.findViewById(R.id.button1);

			if (started) {// if the view is recreated, we set text to stop
				b.setText("STOP");
			}

			b.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					if (!started) {
						// http://stackoverflow.com/questions/5293615/how-can-i-get-root-permissions-through-the-android-sdk
						// IS WRONG

						if (initialize()) {

							finalCheckBeforeStart();

							startVisualizer();
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
