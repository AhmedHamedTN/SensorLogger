package at.jku.tfeichtinger.sensorlogger.database;

import java.sql.SQLException;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import at.jku.tfeichtinger.sensorlogger.R;
import at.jku.tfeichtinger.sensorlogger.entities.ActivityLabel;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {

	private static DatabaseHelper instance = null;

	/**
	 * Get an instance of this database helper.
	 */
	public static DatabaseHelper getInstance() {
		return instance;
	}

	// debugging tag
	private static final String TAG = DatabaseHelper.class.getName();

	private static final String DATABASE_NAME = "markethours.db";
	private static final int DATABASE_VERSION = 4;

	private Dao<ActivityLabel, Integer> activityLabelDao = null;

	public DatabaseHelper(final Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION, R.raw.ormlite_config);

		DatabaseHelper.instance = this;
		DataImporter.setHelper(instance);
		DataImporter.setContext(context);
	}

	@Override
	public void onCreate(SQLiteDatabase arg0, ConnectionSource arg1) {
		try {
			Log.i(TAG, "onCreate");
			TableUtils.createTable(connectionSource, ActivityLabel.class);

			DataImporter.importData();
		} catch (final SQLException e) {
			// TODO
			Log.e(TAG, "Can't create database", e);
		}

	}

	@Override
	public void onUpgrade(SQLiteDatabase db, ConnectionSource connectionSource, int oldVersion, int newVersion) {
		try {
			Log.i(TAG, "onUpgrade");
			TableUtils.dropTable(connectionSource, ActivityLabel.class, true);
			onCreate(db, connectionSource);
		} catch (SQLException e) {
			// TODO
			Log.e(TAG, "Can't drop databases", e);
		}
	}

	public Dao<ActivityLabel, Integer> getActivityLabelDao() throws SQLException {
		if (activityLabelDao == null) {
			return getDao(ActivityLabel.class);
		}
		return activityLabelDao;
	}

	/**
	 * Close the database connections and clear any cached DAOs.
	 */
	@Override
	public void close() {
		super.close();
		activityLabelDao = null;
	}

}
