package com.yadli.luminara;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.stericson.RootTools.*;
import com.stericson.RootTools.exceptions.RootDeniedException;
import com.stericson.RootTools.execution.CommandCapture;

import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.app.AlertDialog;
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

	static boolean started = false;
	static Visualizer visualizer = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

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
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {

		public PlaceholderFragment() {
		}

		static float loMax, miMax, hiMax;
		static final float minimalMax = 100.0f;
		static final float decayStrength = 0.99f;
		static float rms = 0.0f;
		static float rmsMax = 1.0f;
		static final float rmsMinMax = 1.0f;
		static float intensityMin = 0.5f;
		static float intensityMax = 0.51f;
		static float intensityHistory = 0.0f;

		static private File dir;
		static private File redFile;
		static private File greenFile;
		static private File blueFile;

		public void OnWaveData(byte[] bytes) {
		}

		public void OnFFTData(byte[] bytes) {
			int len = bytes.length;
			int index = 2;// 0 and 1 are real
							// part of F0 and
							// Fn/2

			int low = 0, mid = 0, high = 0;
			int lowBound = len / 50; // 20k / 50 =
										// 400Hz as
										// lowBound
			int midBound = len / 20;// 20k / 20 = 1Khz
									// as midbound

			rms = 0.0f;

			for (; index < len; index += 2) {
				int re = bytes[index];
				int im = bytes[index + 1];
				int val = (int) Math.sqrt(re * re + im * im);

				if (index <= midBound)
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

			if (intensity < intensityHistory * 2)
			{
				intensity = (float) (intensity * 0.1 + intensityHistory * 0.9);
				intensity *= 0.7;
			}else
				intensity = 1;
			intensityHistory = intensity;

			// intensity = (float) Math.sqrt(intensity);
			// intensity = (float) Math.sqrt(intensity);

			// if (intensity > intensityMax * 0.6)
			// Log.d("visualizer", "Trigger");

			Log.d("visualizer", "range = \t" + range + "\tmax = \t"
					+ intensityMax + "\tmin=\t" + intensityMin + "\tval=\t"
					+ intensity);

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

			FileOutputStream os;
			try {
				os = new FileOutputStream(redFile);
				os.write(Integer.toString(loVal).getBytes());
				os.close();
				os = new FileOutputStream(greenFile);
				os.write(Integer.toString(miVal).getBytes());
				os.close();
				os = new FileOutputStream(blueFile);
				os.write(Integer.toString(hiVal).getBytes());
				os.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		private int boost(int val) {
			val *= 1.5;
			if (val > 255)
				val = 255;
			return val;
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container,
					false);
			final Button b = (Button) rootView.findViewById(R.id.button1);
			b.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					if (!started) {
						// http://stackoverflow.com/questions/5293615/how-can-i-get-root-permissions-through-the-android-sdk
						// IS WRONG
						try {

							dir = getActivity().getFilesDir();
							Log.d("visualizer",
									"dir = " + dir.getAbsolutePath());
							dir.mkdirs();

							redFile = new File(dir, "red");
							greenFile = new File(dir, "green");
							blueFile = new File(dir, "blue");

							if (RootTools.isAccessGiven()) {
								CommandCapture cmd = new CommandCapture(
										0,
										"chmod 666 /sys/class/leds/led:rgb_red/brightness",
										"chmod 666 /sys/class/leds/led:rgb_green/brightness",
										"chmod 666 /sys/class/leds/led:rgb_blue/brightness",
										"chown root:sdard_r /sys/class/leds/led:rgb_red/brightness",
										"chown root:sdard_r /sys/class/leds/led:rgb_green/brightness",
										"chown root:sdard_r /sys/class/leds/led:rgb_blue/brightness",
										"ln -s /sys/class/leds/led:rgb_red/brightness "
												+ redFile.getAbsolutePath(),
										"ln -s /sys/class/leds/led:rgb_green/brightness "
												+ greenFile.getAbsolutePath(),
										"ln -s /sys/class/leds/led:rgb_blue/brightness "
												+ blueFile.getAbsolutePath());
								RootTools.getShell(true).add(cmd);
							}

							// Runtime.getRuntime()
							// .exec("su -c '"
							// + "'");
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						} catch (TimeoutException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (RootDeniedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						visualizer = new Visualizer(0);
						visualizer.setCaptureSize(Visualizer
								.getCaptureSizeRange()[1]);
						hiMax = miMax = loMax = minimalMax;
						visualizer.setDataCaptureListener(
								new Visualizer.OnDataCaptureListener() {
									public void onWaveFormDataCapture(
											Visualizer visualizer,
											byte[] bytes, int samplingRate) {
										OnWaveData(bytes);
									}

									public void onFftDataCapture(
											Visualizer visualizer,
											byte[] bytes, int samplingRate) {
										OnFFTData(bytes);
									}

								}, Visualizer.getMaxCaptureRate(), false, true);

						visualizer.setEnabled(true);
						// else {
						//
						// AlertDialog.Builder dlgBuilder = new
						// AlertDialog.Builder(
						// getActivity());
						// dlgBuilder.setMessage("Failed to get root access.");
						// dlgBuilder.setTitle("Alert");
						// dlgBuilder.show();
						// }

						b.setText("STOP");
					} else {
						if (visualizer != null)
							visualizer.release();
						visualizer = null;
						b.setText("START");
					}

					started = !started;
				}
			});

			return rootView;
		}
	}

}
