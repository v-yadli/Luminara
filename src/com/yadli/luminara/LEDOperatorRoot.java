package com.yadli.luminara;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import android.util.Log;

import com.stericson.RootTools.containers.RootClass;

@RootClass.Candidate
public class LEDOperatorRoot {

	private HashMap<String, File> leds;

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

	public static int commit_clk = 0;

	private static void d(String msg) {
		System.out.println("luminara-root:\t\t" + msg);
	}

	public LEDOperatorRoot(RootClass.RootArgs args) {

		d("Root loop started");

		String[] arguments = args.args;
		// for(String s:arguments)
		// d(s);
		//
		File dir = new File(arguments[0]);
		d("dir = " + dir.getAbsolutePath());
		leds = new HashMap<String, File>();
		for (int idx = 1; idx < arguments.length; idx += 2) {
			File f = new File(arguments[idx + 1]);
			leds.put(arguments[idx], f);
			d("found interface " + arguments[idx] + " at "
					+ f.getAbsolutePath());
		}
		//
		File commitFile = new File(dir, "commit");
		File valueFile = new File(dir, "value");
		int loopCount = 0;
		boolean stopCommandReceived = false;
		while (!stopCommandReceived) {
			try {
				Thread.sleep(20);
				++loopCount;
				if(loopCount == 300)
				{
					d("Heart is still beating");
					loopCount = 0;
				}
				if (commitFile.exists()) {

					int loVal, miVal, hiVal;
					BufferedReader br = new BufferedReader(new FileReader(
							valueFile));
					String[] vals = br.readLine().split(" ");
					br.close();
					valueFile.delete();
					commitFile.delete();

					loVal = Integer.parseInt(vals[0]);
					miVal = Integer.parseInt(vals[1]);
					hiVal = Integer.parseInt(vals[2]);
					
					if(loVal < 0 || miVal < 0 || hiVal < 0)
					{
						stopCommandReceived = true;
						loVal = miVal = hiVal = 0;
					}

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
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
