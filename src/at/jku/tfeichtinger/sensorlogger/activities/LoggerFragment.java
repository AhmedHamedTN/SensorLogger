package at.jku.tfeichtinger.sensorlogger.activities;

import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import at.jku.tfeichtinger.sensorlogger.R;
import at.jku.tfeichtinger.sensorlogger.database.DatabaseHelper;
import at.jku.tfeichtinger.sensorlogger.entities.ActivityLabel;
import at.jku.tfeichtinger.sensorlogger.services.SensorLoggerService;
import at.jku.tfeichtinger.sensorlogger.services.SensorLoggerService.ServiceState;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;

public class LoggerFragment extends SherlockFragment {
	private static final String TAG = LoggerFragment.class.getCanonicalName();

	/** A messenger for receiving messages from the service. */
	private final Messenger callbackMessenger = new Messenger(new CallbackHandler(this));
	/** Indicates whether the service is bound to this activity. */
	private boolean mIsBound;
	/** A messenger for sending messages to the service. */
	private Messenger sensorLoggerServiceMessenger;
	/** The grid adapter. */
	private ActivityLabelAdapter activityLabelAdapter;

	/** The database helper. */
	private DatabaseHelper dbHelper;

	private GridView activityLabelGrid;

	private final ServiceConnection sensorLoggerServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(final ComponentName name, final IBinder service) {
			sensorLoggerServiceMessenger = new Messenger(service);

			try {
				final Message message = Message.obtain(null, SensorLoggerService.MSG_REGISTER_CLIENT);
				message.replyTo = callbackMessenger;
				sensorLoggerServiceMessenger.send(message);
			} catch (final RemoteException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}

		@Override
		public void onServiceDisconnected(final ComponentName name) {
			sensorLoggerServiceMessenger = null;
		}
	};

	private final OnItemClickListener onGridItemClickListener = new OnItemClickListener() {
		@Override
		public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
			try {
				final Message message = Message.obtain(null, SensorLoggerService.MSG_START_LOGGING);

				final ActivityLabel label = (ActivityLabel) activityLabelGrid.getItemAtPosition(position);
				final Bundle bundle = new Bundle();
				bundle.putString(SensorLoggerService.DATA_ACTIVITY_LABEL, label.getLabel());
				bundle.putInt(SensorLoggerService.DATA_ACTIVITY_ID, label.getId());
				message.setData(bundle);

				sensorLoggerServiceMessenger.send(message);
			} catch (final RemoteException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
	};

	private void createHelpDialog() {
		final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		final LayoutInflater inflater = getActivity().getLayoutInflater();

		final View dialogView = inflater.inflate(R.layout.dialog_help_logger, null);
		WebView webview = (WebView) dialogView.findViewById(R.id.help_logger_webview);
		webview.loadUrl("file:///android_asset/help_logger.html");

		builder.setView(dialogView).setTitle("FAQ");
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				//
			}
		});

		final AlertDialog alertDialog = builder.create();
		alertDialog.show();
	}

	/**
	 * 
	 * @param editLabel
	 *            empty string if create a new label; the label if editing;
	 */
	private void createUpdateCreateLabelDialog(final ActivityLabel label) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		final LayoutInflater inflater = getActivity().getLayoutInflater();

		final View dialogView = inflater.inflate(R.layout.dialog_add_label, null);
		final EditText labelText = (EditText) dialogView.findViewById(R.id.labelname);
		labelText.setText(label.getLabel());

		builder.setView(dialogView);
		builder.setTitle(R.string.dialog_create_label);
		// Add action buttons
		builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int id) {
				final String text = labelText.getText().toString();
				if (text != null && !text.isEmpty()) {
					try {
						Dao<ActivityLabel, Integer> dao = getDbHelper().getActivityLabelDao();
						label.setLabel(text);

						if (label.getId() == null) {
							dao.create(label);
						} else {
							dao.update(label);
						}

						refreshGrid();
					} catch (final SQLException e) {
						Log.e(TAG, e.getMessage(), e);
					}
				}
			}
		});
		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(final DialogInterface dialog, final int id) {
				dialog.cancel();

			}
		});

		final AlertDialog alertDialog = builder.create();
		alertDialog.show();
	}

	private void doBindService() {
		final Intent bindIntent = new Intent(getActivity(), SensorLoggerService.class);
		getActivity().startService(bindIntent);
		mIsBound = getActivity().bindService(bindIntent, sensorLoggerServiceConnection, Context.BIND_AUTO_CREATE | Service.START_STICKY);
	}

	void doUnbindService() {
		if (mIsBound) {
			// Detach our existing connection.
			getActivity().unbindService(sensorLoggerServiceConnection);
			mIsBound = false;
		}
	}

	@Override
	public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
		inflater.inflate(R.menu.activity_logger, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.fragment_logger, container, false);

		activityLabelGrid = (GridView) view.findViewById(R.id.activities_grid);
		activityLabelGrid.setOnItemClickListener(onGridItemClickListener);

		// if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
		// TODO check if needed
		// activityLabelGrid.setChoiceMode(GridView.CHOICE_MODE_SINGLE);
		// }
		registerForContextMenu(activityLabelGrid);

		activityLabelAdapter = new ActivityLabelAdapter(getActivity());
		activityLabelGrid.setAdapter(activityLabelAdapter);

		setHasOptionsMenu(true);

		return view;
	}

	@Override
	public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		final android.view.MenuInflater inflater = getSherlockActivity().getMenuInflater();
		inflater.inflate(R.menu.label_context, menu);
	}

	private DatabaseHelper getDbHelper() {
		if (dbHelper == null) {
			dbHelper = (DatabaseHelper) OpenHelperManager.getHelper(getActivity(), DatabaseHelper.class);
		}
		return dbHelper;
	}

	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {
		case R.id.menu_label_delete:
			try {
				final Dao<ActivityLabel, Integer> dao = getDbHelper().getActivityLabelDao();
				dao.delete(dao.queryForId((int) info.id));
				refreshGrid();
			} catch (final SQLException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			return true;
		case R.id.menu_label_edit:
			try {
				final Dao<ActivityLabel, Integer> dao = getDbHelper().getActivityLabelDao();
				createUpdateCreateLabelDialog(dao.queryForId((int) info.id));
				refreshGrid();
			} catch (SQLException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		doUnbindService();
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_stop_logging:
			stopLogging();
			return true;
		case R.id.menu_add_label:
			createUpdateCreateLabelDialog(new ActivityLabel());
			refreshGrid();
			return true;
		case R.id.menu_help_logging:
			createHelpDialog();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getActivity();
		// first run? -> show FAQ dialog
		boolean firstrun = getActivity().getSharedPreferences("PREFERENCE", Context.MODE_PRIVATE).getBoolean("firstrun", true);
		if (firstrun) {
			createHelpDialog();
			// Save the state
			getActivity().getSharedPreferences("PREFERENCE", Context.MODE_PRIVATE).edit().putBoolean("firstrun", false).commit();
		}

		doBindService();
	}

	@Override
	public void onResume() {
		super.onResume();
		refreshGrid();
	}

	private void refreshGrid() {
		try {
			final List<ActivityLabel> allLabels = getDbHelper().getActivityLabelDao().queryForAll();
			activityLabelAdapter.setLabels(allLabels);
		} catch (final SQLException e) {
			Log.e(TAG, e.getMessage(), e);
		}

		activityLabelGrid.invalidate();
	}

	private void showStatus(final Bundle data) {
		final ServiceState status = SensorLoggerService.ServiceState.valueOf(data.getString(SensorLoggerService.DATA_STATUS));
		if (status == ServiceState.LOGGING) {
			activityLabelAdapter.setHighlightedId(data.getInt(SensorLoggerService.DATA_ACTIVITY_ID));
		} else {
			activityLabelAdapter.disableHightlight();
		}
		refreshGrid();
	}

	private void stopLogging() {
		try {
			final Message message = Message.obtain(null, SensorLoggerService.MSG_STOP_LOGGING);
			sensorLoggerServiceMessenger.send(message);
		} catch (final RemoteException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private class ActivityLabelAdapter extends BaseAdapter {

		private final Context context;
		private List<ActivityLabel> labels;

		private Integer highlightedId;

		public ActivityLabelAdapter(final Context context, final List<ActivityLabel> labels) {
			this.context = context;
			this.labels = labels;
		}

		public ActivityLabelAdapter(final Context context) {
			this(context, Collections.<ActivityLabel> emptyList());
		}

		public void setLabels(final List<ActivityLabel> labels) {
			this.labels = labels;
			notifyDataSetChanged();
		}

		public void disableHightlight() {
			highlightedId = null;
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return labels.size();
		}

		@Override
		public Object getItem(final int position) {
			return labels.get(position);
		}

		@Override
		public long getItemId(final int position) {
			return labels.get(position).getId();
		}

		@Override
		public View getView(final int position, final View convertView, final ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				final LayoutInflater vi = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.activitylabel_cell, null);
			}

			final TextView name = (TextView) v.findViewById(R.id.activitylabel_cell_name);
			name.setText(labels.get(position).getLabel());

			if (highlightedId != null && labels.get(position).getId() == highlightedId) {
				v.setBackgroundDrawable(getResources().getDrawable(R.drawable.cell_background_active));
			} else {
				v.setBackgroundDrawable(getResources().getDrawable(R.drawable.cell_background));
			}

			return v;
		}

		protected void setHighlightedId(final int id) {
			highlightedId = id;
			notifyDataSetChanged();
		}
	}

	/**
	 * Call-back handler class for communication with the SensorLoggerService
	 */
	private static class CallbackHandler extends Handler {

		WeakReference<LoggerFragment> mFrag;

		CallbackHandler(final LoggerFragment loggerFragment) {
			mFrag = new WeakReference<LoggerFragment>(loggerFragment);
		}

		@Override
		public void handleMessage(final Message msg) {
			final LoggerFragment loggerFragment = mFrag.get();
			if (msg.what == SensorLoggerService.MSG_STATUS) {
				loggerFragment.showStatus(msg.getData());
			}
		}
	}
}
