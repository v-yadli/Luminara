package com.yadli.luminara;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import android.app.Activity;
import android.util.Log;

import com.stericson.RootTools.*;
import com.stericson.RootTools.exceptions.RootDeniedException;
import com.stericson.RootTools.execution.Command;
import com.stericson.RootTools.execution.CommandCapture;
import com.stericson.RootTools.execution.JavaCommandCapture;
import com.stericson.RootTools.execution.Shell;

public class LEDOperator {

	// ================================== THEY CANNOT COMMUNICATE WITH EACH
	// OTHER ==========================

	static private File dir;
	static private HashMap<String, File> leds;

	public static void probe_rgb() {
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
		f = new File("/sys/class/leds/led_r/brightness");
		if (f.exists())
			leds.put("red", f);
		f = new File("/sys/class/leds/lm3533-red/brightness");
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
		f = new File("/sys/class/leds/lm3533-blue/brightness");
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
		f = new File("/sys/class/leds/lm3533-green/brightness");
		if (f.exists())
			leds.put("green", f);
	}

	public static void probe_logo() {
		File f;
		f = new File("/sys/class/leds/logo-backlight_1/brightness");
		if (f.exists())
			leds.put("logo1", f);

		f = new File("/sys/class/leds/logo-backlight_2/brightness");
		if (f.exists())
			leds.put("logo2", f);
	}

	public static void probe_special() {
		File f;

		// TODO detect LT26 device name
		f = new File("/sys/class/leds/button-backlight/brightness");
		if (f.exists())
			leds.put("button", f);

		f = new File("/sys/class/leds/R/brightness");
		if (f.exists())
			leds.put("back-red", f);
		f = new File("/sys/class/leds/G/brightness");
		if (f.exists())
			leds.put("back-green", f);
		f = new File("/sys/class/leds/B/brightness");
		if (f.exists())
			leds.put("back-blue", f);

		// Xperia SP
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

		// Moto G

		f = new File("/sys/class/leds/white/brightness");
		if (f.exists())
			leds.put("white", f);
	}

	public static void probe_trigger() {
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

	private static CommandCapture hackBrightnessFile(String path, File target) {
		// AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(
		// getActivity());
		// dlgBuilder.setMessage("putting "+path+" to "+
		// target.getAbsolutePath());
		// dlgBuilder.setTitle("Debug");
		// dlgBuilder.show();
		return new CommandCapture(0, "chmod 666 " + path,

		"rm -f " + target.getAbsolutePath(),

		"ln -s " + path + " " + target.getAbsolutePath());
	}

	private static boolean initialized = false;

	private static Shell loopShell;

	public static boolean initialize(Activity activity) {

		if (initialized)
			return true;

		leds = new HashMap<String, File>();

		try {
			dir = activity.getFilesDir();

			stop();
			try {
				Thread.sleep(300);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}// wait for any possible old daemon to die

			probe_rgb();
			probe_logo();// for Xperia TX
			probe_special(); // for my beloved Xperia SL
			probe_trigger(); // for Nexus 5

			if (RootTools.isAccessGiven()) {

				Shell sh = RootTools.getShell(true);

				CommandCapture rmCmd = new CommandCapture(0, "rm -f "
						+ dir.getAbsolutePath() + "/*");
				sh.add(rmCmd);
				while (!rmCmd.isFinished())
					Thread.sleep(1);

				for (String key : leds.keySet()) {
					File f = new File(dir, key);
					CommandCapture cmd = hackBrightnessFile(leds.get(key)
							.getAbsolutePath(), f);
					sh.add(cmd);
					while (!cmd.isFinished())
						Thread.sleep(1);
					leds.put(key, f);
				}

				RootTools.debugMode = true;
				// loopShell = RootTools.getShell(true);

				InputStream is = activity.getResources().openRawResource(
						R.raw.anbuild);
				byte[] content = new byte[is.available()];
				is.read(content);
				FileOutputStream dexOS = new FileOutputStream(new File(dir,
						"anbuild.dex"));
				dexOS.write(content);
				dexOS.close();

				String loopCmdString = "com.yadli.luminara.LEDOperatorRoot "
						+ dir.getAbsolutePath();
				for (String key : leds.keySet()) {
					loopCmdString += " " + key;
					loopCmdString += " " + leds.get(key).getAbsolutePath();
				}

				JavaCommandCapture loopCmd = new JavaCommandCapture(42, 0,
						activity, loopCmdString);
				sh.add(loopCmd);

				initialized = true;
				return true;
			}

			// Runtime.getRuntime()
			// .exec("su -c '"
			// + "'");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public static boolean onLazyCommit = false;
	public static int lazyLatch = 0;
	static final int LAZY_LATCH_THRESHOLD = 1000;

	public static void commit(int loVal, int miVal, int hiVal) {
		try {
			boolean needCommit = true;
			File commitFile = new File(dir, "commit");
			File valueFile = new File(dir, "value");

			if (loVal == 0 && miVal == 0 && hiVal == 0) {
				if (onLazyCommit)
					needCommit = false;
				else {
					++lazyLatch;

					if (lazyLatch > LAZY_LATCH_THRESHOLD) {
						Log.d("visualizer", "entering lazy commit mode");
						onLazyCommit = true;
						needCommit = false;
					}
				}
			} else {
				onLazyCommit = false;
				lazyLatch = 0;
			}
			if (commitFile.exists())
				needCommit = false;

			if (needCommit) {
				FileOutputStream fo = new FileOutputStream(valueFile);
				fo.write((loVal + " " + miVal + " " + hiVal).getBytes());
				fo.close();

				fo = new FileOutputStream(commitFile);
				fo.write(0);
				fo.close();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void stop() {
		File commitFile = new File(dir, "commit");
		File valueFile = new File(dir, "value");
		try {
			FileOutputStream fo = new FileOutputStream(valueFile);
			fo.write((-1 + " " + -1 + " " + -1).getBytes());
			fo.close();

			fo = new FileOutputStream(commitFile);
			fo.write(0);
			fo.close();
			initialized = false;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
