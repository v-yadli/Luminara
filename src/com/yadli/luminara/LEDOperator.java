package com.yadli.luminara;

import java.io.FileOutputStream;
import java.io.IOException;

import com.stericson.RootTools.*;
import com.stericson.RootTools.exceptions.RootDeniedException;
import com.stericson.RootTools.execution.Command;
import com.stericson.RootTools.execution.CommandCapture;
import com.stericson.RootTools.execution.Shell;
import com.stericson.RootTools.containers.RootClass;

@RootClass.Candidate
public class LEDOperator {
	public LEDOperator(RootClass.RootArgs args)
	{
		
	}
	public static boolean needCommit = false;
	public static boolean onLazyCommit = false;
	public static int lazyLatch = 0;
	public static int commit_clk = 0;

	public static void commit(int loVal, int miVal, int hiVal) {
		try {
			needCommit = false;

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
				// TODO Oppo shineled doesn't use standard interface.

				// Moto G

				postValueToFile((hiVal + miVal + loVal) / 3, "white");

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
			e.printStackTrace();
		}
	}
	public void postValueToFile(int val, String key) throws IOException {
		File f;
		FileOutputStream os;
		if (leds != null && leds.containsKey(key)) {
			f = leds.get(key);
			os = new FileOutputStream(f);
			os.write(Integer.toString(val).getBytes());
			os.close();
		}
	}

	public void postValueToFile(int val1, int val2, String key)
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

}
