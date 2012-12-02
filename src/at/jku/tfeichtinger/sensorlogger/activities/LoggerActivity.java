package at.jku.tfeichtinger.sensorlogger.activities;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.Menu;
import at.jku.tfeichtinger.sensorlogger.R;
import at.jku.tfeichtinger.sensorlogger.database.DatabaseHelper;

import com.j256.ormlite.android.apptools.OrmLiteBaseActivity;

public class LoggerActivity extends OrmLiteBaseActivity<DatabaseHelper> {

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_logger);

		final ActionBar bar = getActionBar();
		bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		Tab loggerTag = bar.newTab().setText(R.string.tab_logger)
				.setTabListener(new TabListener<LoggerFragment>(this, "tab_logger", LoggerFragment.class));
		bar.addTab(loggerTag);
		Tab filesTab = bar.newTab().setText(R.string.tab_files)
				.setTabListener(new TabListener<FilesFragment>(this, "tab_files", FilesFragment.class));
		bar.addTab(filesTab);

	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_logger, menu);
		return true;
	}

	protected class TabListener<T extends Fragment> implements ActionBar.TabListener {
		private Fragment fragment;
		private final Class<T> clazz;
		private final String tag;
		private final Activity activity;

		/**
		 * Constructor used each time a new tab is created.
		 * 
		 * @param activity
		 *            The host Activity, used to instantiate the fragment
		 * @param tag
		 *            The identifier tag for the fragment
		 * @param clz
		 *            The fragment's Class, used to instantiate the fragment
		 */
		public TabListener(final Activity activity, final String tag, final Class<T> clz) {
			this.activity = activity;
			this.tag = tag;
			this.clazz = clz;
		}

		@Override
		public void onTabSelected(final Tab tab, final FragmentTransaction ft) {
			fragment = Fragment.instantiate(activity, clazz.getName());
			ft.replace(android.R.id.content, fragment, tag);
		}

		@Override
		public void onTabReselected(final Tab tab, final FragmentTransaction ft) {
			//
		}

		@Override
		public void onTabUnselected(final Tab tab, final FragmentTransaction ft) {
			//
		}
	}

}
