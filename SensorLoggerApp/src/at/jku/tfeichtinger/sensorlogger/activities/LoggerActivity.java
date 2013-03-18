package at.jku.tfeichtinger.sensorlogger.activities;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import at.jku.tfeichtinger.sensorlogger.R;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.app.SherlockFragmentActivity;

public class LoggerActivity extends SherlockFragmentActivity  {

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_logger);

		setUpActionBar();
	}

	private void setUpActionBar() {
		final ActionBar bar = getSupportActionBar();
		bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		bar.setDisplayShowTitleEnabled(false);
		bar.setDisplayShowHomeEnabled(false);

		Tab loggerTag = bar.newTab().setText(R.string.tab_logger)
				.setTabListener(new TabListener<LoggerFragment>(this, "tab_logger", LoggerFragment.class));
		bar.addTab(loggerTag);
		Tab filesTab = bar.newTab().setText(R.string.tab_files)
				.setTabListener(new TabListener<FilesFragment>(this, "tab_files", FilesFragment.class));
		bar.addTab(filesTab);
	}

	protected class TabListener<T extends SherlockFragment> implements ActionBar.TabListener {
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
