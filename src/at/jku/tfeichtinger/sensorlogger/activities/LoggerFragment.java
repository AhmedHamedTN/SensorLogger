package at.jku.tfeichtinger.sensorlogger.activities;

import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.util.List;

import android.app.AlertDialog;
import android.app.Fragment;
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
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
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

public class LoggerFragment extends Fragment {
	private static final String TAG = LoggerFragment.class.getCanonicalName();

	/** A messenger for receiving messages from the service. */
	private final Messenger callbackMessenger = new Messenger(new CallbackHandler(this));
	/** Indicates whether the service is bound to this activity. */
	private boolean mIsBound;
	/** A messenger for sending messages to the service. */
	private Messenger sensorLoggerServiceMessenger;
	/** The grid adapter. */
	private ActivityLabelAdapter adapter;
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
				bundle.putString(SensorLoggerService.DATA_ACTIVITY_ID, label.getId() + "");
				message.setData(bundle);

				sensorLoggerServiceMessenger.send(message);
			} catch (final RemoteException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
	};

	/**
	 * 
	 * @param editLabel
	 *            empty string if create a new label; the label if editing;
	 */
	private void createAddLabelDialog(String editLabel) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		final LayoutInflater inflater = getActivity().getLayoutInflater();

		final View dialogView = inflater.inflate(R.layout.dialog_add_label, null);
		final EditText labelText = (EditText) dialogView.findViewById(R.id.labelname);
		labelText.setText(editLabel);

		builder.setView(dialogView).setTitle(R.string.dialog_create_label)
		// Add action buttons
				.setPositiveButton(R.string.create, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog, final int id) {
						final String text = labelText.getText().toString();
						if (text != null && !text.isEmpty()) {
							final ActivityLabel label = new ActivityLabel(text);
							try {
								getDbHelper().getActivityLabelDao().create(label);
							} catch (SQLException e) {
								Log.e(TAG, e.getMessage(), e);
							}
						}
					}
				}).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
					public void onClick(final DialogInterface dialog, final int id) {
						dialog.cancel();

					}
				});

		AlertDialog alertDialog = builder.create();
		alertDialog.show();
	}

	private void doBindService() {
		final Intent bindIntent = new Intent(getActivity(), SensorLoggerService.class);
		mIsBound = getActivity().bindService(bindIntent, sensorLoggerServiceConnection, Context.BIND_AUTO_CREATE | Service.START_STICKY);
	}

	void doUnbindService() {
		if (mIsBound) {
			// Detach our existing connection.
			getActivity().unbindService(sensorLoggerServiceConnection);
			mIsBound = false;
		}
	}

	private void fillGrid() throws SQLException {
		final List<ActivityLabel> allLabels = getDbHelper().getActivityLabelDao().queryForAll();
		adapter = new ActivityLabelAdapter(getActivity(), allLabels);
		activityLabelGrid.setAdapter(adapter);
	}

	private DatabaseHelper getDbHelper() {
		if (dbHelper == null) {
			dbHelper = DatabaseHelper.getInstance();
		}
		return dbHelper;
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

		activityLabelGrid.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE_MODAL);
		activityLabelGrid.setMultiChoiceModeListener(new MultiChoiceModeListener() {

			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				return false;
			}

			@Override
			public void onDestroyActionMode(ActionMode mode) {
			}

			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.label_context, menu);
				return true;
			}

			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				switch (item.getItemId()) {
				case R.id.menu_label_delete:
					try {
						deleteSelectedActivityLabels();
						refreshGrid();
					} catch (SQLException e) {
						Log.e(TAG, e.getMessage(), e);
					}
					return true;
				}
				return false;
			}

			private void deleteSelectedActivityLabels() throws SQLException {
				final int len = activityLabelGrid.getCount();
				for (int i = 0; i < len; i++) {
					final SparseBooleanArray checked = activityLabelGrid.getCheckedItemPositions();
					if (checked.get(i)) {
						ActivityLabel label = (ActivityLabel) adapter.getItem(i);
						getDbHelper().getActivityLabelDao().delete(label);
					}
				}
			}

			@Override
			public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
			}
		});

		setHasOptionsMenu(true);

		return view;
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
			createAddLabelDialog("");
			refreshGrid();
			return true;
		}
		return false;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		doBindService();
	}

	@Override
	public void onResume() {
		super.onResume();
		refreshGrid();
	}

	private void refreshGrid() {
		try {
			fillGrid();
			activityLabelGrid.invalidate();
		} catch (SQLException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void showStatus(final Bundle data) {
		final ServiceState status = SensorLoggerService.ServiceState.valueOf(data.getString(SensorLoggerService.DATA_STATUS));
		if (status == ServiceState.LOGGING) {
			adapter.setHighlightedId(Integer.parseInt(data.getString(SensorLoggerService.DATA_ACTIVITY_ID)));
		} else {
			adapter.disableHightlight();
		}
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
		private final List<ActivityLabel> labels;

		private Integer highlightedId;

		public ActivityLabelAdapter(final Context context, final List<ActivityLabel> labels) {
			this.context = context;
			this.labels = labels;
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

		CallbackHandler(LoggerFragment loggerFragment) {
			mFrag = new WeakReference<LoggerFragment>(loggerFragment);
		}

		@Override
		public void handleMessage(final Message msg) {
			LoggerFragment loggerFragment = mFrag.get();
			if (msg.what == SensorLoggerService.MSG_STATUS) {
				loggerFragment.showStatus(msg.getData());
			}
		}
	}
}
