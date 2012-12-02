package at.jku.tfeichtinger.sensorlogger.activities;

import java.sql.SQLException;
import java.util.List;

import android.app.Fragment;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
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
	private final Messenger callbackMessenger = new Messenger(new CallbackHandler());
	/** Indicates whether the service is bound to this activity. */
	private boolean mIsBound;
	/** A messenger for sending messages to the service. */
	private Messenger sensorLoggerService;
	/** The grid adapter. */
	private ActivityLabelAdapter adapter;

	/* UI stuff ----------------------------------------------- */
	private GridView activityLabelGrid;
	/** The button for stopping the logging process. */
	private Button stopButton;

	private final ServiceConnection sensorLoggerServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(final ComponentName name, final IBinder service) {
			sensorLoggerService = new Messenger(service);

			try {
				final Message message = Message.obtain(null, SensorLoggerService.MSG_REGISTER_CLIENT);
				message.replyTo = callbackMessenger;
				sensorLoggerService.send(message);
			} catch (final RemoteException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}

		@Override
		public void onServiceDisconnected(final ComponentName name) {
			sensorLoggerService = null;
		}
	};

	private final OnClickListener onStopButtonClickListener = new OnClickListener() {

		@Override
		public void onClick(final View v) {
			try {
				final Message message = Message.obtain(null, SensorLoggerService.MSG_STOP_LOGGING);
				sensorLoggerService.send(message);
			} catch (final RemoteException e) {
				Log.e(TAG, e.getMessage(), e);
			}
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

				sensorLoggerService.send(message);
			} catch (final RemoteException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
	};

	private void fillGrid() throws SQLException {
		final DatabaseHelper dbHelper = DatabaseHelper.getInstance();

		final List<ActivityLabel> allLabels = dbHelper.getActivityLabelDao().queryForAll();
		adapter = new ActivityLabelAdapter(getActivity(), allLabels);
		activityLabelGrid.setAdapter(adapter);
	}

	private void showStatus(final Bundle data) {
		final ServiceState status = SensorLoggerService.ServiceState.valueOf(data
				.getString(SensorLoggerService.DATA_STATUS));
		if (status == ServiceState.LOGGING) {
			adapter.setHighlightedId(Integer.parseInt(data.getString(SensorLoggerService.DATA_ACTIVITY_ID)));
		} else {
			adapter.disableHightlight();
		}
	}

	void doUnbindService() {
		if (mIsBound) {
			// Detach our existing connection.
			getActivity().unbindService(sensorLoggerServiceConnection);
			mIsBound = false;
		}
	}

	private void doBindService() {
		getActivity().bindService(new Intent(getActivity(), SensorLoggerService.class), sensorLoggerServiceConnection,
				Context.BIND_AUTO_CREATE | Service.START_STICKY);
		mIsBound = true;
	}

	/* Lifecycle Methods -------------------------------------------- */

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.fragment_logger, container, false);

		activityLabelGrid = (GridView) view.findViewById(R.id.activities_grid);
		activityLabelGrid.setOnItemClickListener(onGridItemClickListener);

		stopButton = (Button) view.findViewById(R.id.stopButton);
		stopButton.setOnClickListener(onStopButtonClickListener);

		return view;
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		doBindService();
	}

	@Override
	public void onResume() {
		super.onResume();

		try {
			fillGrid();
		} catch (final SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		doUnbindService();
	}

	private class ActivityLabelAdapter extends BaseAdapter {

		private final Context context;
		private final List<ActivityLabel> labels;

		private Integer highlightedId;

		public ActivityLabelAdapter(final Context context, final List<ActivityLabel> labels) {
			this.context = context;
			this.labels = labels;
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

		protected void setHighlightedId(int id) {
			highlightedId = id;
			notifyDataSetChanged();
		}

		public void disableHightlight() {
			highlightedId = null;
			notifyDataSetChanged();
		}
	}

	/**
	 * Call-back handler class for communication with the SensorLoggerService
	 */
	private class CallbackHandler extends Handler {
		@Override
		public void handleMessage(final Message msg) {
			if (msg.what == SensorLoggerService.MSG_STATUS) {
				showStatus(msg.getData());
			}
		}
	}
}
