package at.jku.tfeichtinger.sensorlogger.database;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;

import android.content.Context;
import android.content.res.AssetManager;
import at.jku.tfeichtinger.sensorlogger.entities.ActivityLabel;

public class DataImporter {

	private static DatabaseHelper helper;
	private static Context context;

	protected static void importData() {

		final AssetManager assets = getContext().getApplicationContext().getAssets();
		try {
			final String[] datafiles = assets.list("data");

			BufferedReader in;
			for (final String filename : datafiles) {
				in = new BufferedReader(new InputStreamReader(assets.open("data/" + filename)));

				String line;
				while ((line = in.readLine()) != null) {
					final ActivityLabel label = new ActivityLabel(line);
					helper.getActivityLabelDao().create(label);
				}
			}
		} catch (final IOException e) {
			e.printStackTrace();
		} catch (final SQLException e) {
			e.printStackTrace();
		}
	}

	public static DatabaseHelper getHelper() {
		return helper;
	}

	public static void setHelper(final DatabaseHelper helper) {
		DataImporter.helper = helper;
	}

	public static Context getContext() {
		return context;
	}

	public static void setContext(final Context context) {
		DataImporter.context = context;
	}
}
