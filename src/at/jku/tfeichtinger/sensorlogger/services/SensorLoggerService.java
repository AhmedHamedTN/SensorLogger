package at.jku.tfeichtinger.sensorlogger.services;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.widget.Toast;
import at.jku.tfeichtinger.sensorlogger.R;
import at.jku.tfeichtinger.sensorlogger.activities.LoggerActivity;

public class SensorLoggerService extends Service {
	private static final String TAG = SensorLoggerService.class.toString();

	public static final String DATA_ACTIVITY_LABEL = "data_activity_label";
	public static final String DATA_ACTIVITY_ID = "data_activity_id";
	public static final String DATA_STATUS = "data_status";

	public final static int MSG_REGISTER_CLIENT = 0;

	/**
	 * Command to the service to start logging. Sends callbacks to the client.
	 * The Message's replyTo field must be a Messenger of the client where
	 * callbacks should be sent
	 */
	public final static int MSG_START_LOGGING = 1;

	/**
	 * Command to the service to stop logging. Sends callbacks to the client.
	 * The Message's replyTo field must be a Messenger of the client where
	 * callbacks should be sent
	 */
	public final static int MSG_STOP_LOGGING = 2;

	/**
	 * Command to the service to send back its current status. The client will
	 * receive an answer with the same MSG constant.
	 */
	public final static int MSG_STATUS = 3;

	/** */
	private ServiceState state = ServiceState.STOPPED;

	/** Remembers the activity for which the sensors are being logged. */
	private String currentActivity;
	private int currentActivityId;

	/** Target published for clients to send messages to. */
	private Messenger mMessenger;

	/* ********************************************************************
	 * Commands for this service
	 */

	/** The notification manager. Used to show notifications. */
	private NotificationManager mNotificationManager;

	/** The sensor manager. Used to access sensors. */
	private SensorManager mSensorManager;

	/** The attached client, there can only one at any given time. */
	private Messenger mClient;

	/**
	 * Keeps track of the system time when logging was started. Used to
	 * calculate relative time offsets of each sensor event.
	 */
	private long referenceTime;

	/**
	 * Remembers when logging started. Used for creating a file name.
	 */
	private Date startTime;

	/**
	 * Used to keep phone listening to sensor values when the screen is turned
	 * off.
	 */
	private PowerManager.WakeLock partialWakeLock;

	/**
	 * Stores the sensor events of each sensor in a queue. Keys are the IDs of
	 * the used sensors. The SensorEventListener will store the events in this
	 * map. A blocking queue is used to store the events because its content
	 * will be written to files by a separate thread.
	 */
	private Map<Integer, BufferedWriter> sensorMap;

	/**
	 * The sensor event listener. Puts sensor values into the corresponding
	 * queue (depending on the sensor type).
	 */
	private final SensorEventListener sensorEventListener = new SensorEventListener() {
		/**
		 * Conversion factor from nanoseconds to milliseconds
		 * 
		 * <pre>
		 * nano  = 10^-9 
		 * mikro = 10^-6
		 * milli = 10^-3
		 * </pre>
		 */
		private final int CONVERSION_FACTOR = 1000 * 1000;

		/**
		 * 
		 */
		private final DecimalFormat NUMBER_FORMATTER = new DecimalFormat("#.#####");

		@Override
		public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
			// ignore accuracy changes...
		}

		@Override
		public void onSensorChanged(final SensorEvent event) {
			// Log.d(TAG, "onSensorChanged called:" + toCSVString(event));
			try {
				// get the corresponding queue
				final BufferedWriter writer = sensorMap.get(event.sensor.getType());
				if (state == ServiceState.LOGGING) {
					writer.write(toCSVString(event));
				}
			} catch (final IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}

		/**
		 * Creates a .csv representation out of a sensor event.
		 * 
		 * @param event
		 * @return
		 */
		private String toCSVString(final SensorEvent event) {
			return ((event.timestamp / CONVERSION_FACTOR) - referenceTime) + "," + NUMBER_FORMATTER.format(event.values[0]) + ","
					+ NUMBER_FORMATTER.format(event.values[1]) + "," + NUMBER_FORMATTER.format(event.values[2]) + "\n";
		}
	};

	private String getFileName(final String name) {
		final String filename = getLogPath() + "-" + name.toLowerCase().replace(" ", "");
		return filename + ".csv";
	}

	private File getLogFile(final int sensorType) throws IOException {
		// create/get the sub folder for the logs
		final String dir = getLogPath();
		final File path = new File(getExternalFilesDir(null), dir);
		if (!path.exists()) {
			path.mkdir();
		}

		// create new log file
		final String fileName = getFileName(mSensorManager.getSensorList(sensorType).get(0).getName());
		final File file = new File(path, fileName);
		if (!file.exists()) {
			file.createNewFile();
		}
		Log.i(TAG, "file created: " + file.getAbsolutePath());
		return file;
	}

	private String getLogPath() {
		final SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMdd-HHmmssSSS");
		final String path = dateFormat.format(startTime) + "-" + currentActivity.toLowerCase().replace(" ", "");
		return path;
	}

	private void informClientCurrentStatus() {
		try {
			final Message message = Message.obtain(null, MSG_STATUS);
			final Bundle data = new Bundle();
			data.putString(DATA_STATUS, state.toString());
			data.putString(DATA_ACTIVITY_LABEL, currentActivity);
			data.putInt(DATA_ACTIVITY_ID, currentActivityId);
			message.setData(data);
			mClient.send(message);
		} catch (final RemoteException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Return the messenger interface upon binding to this service. Allows the
	 * client to communicate with this service via the messenger interface.
	 */
	@Override
	public IBinder onBind(final Intent intent) {
		return mMessenger.getBinder();
	}

	/**
	 * Called by the system when the service is first created. Do not call this
	 * method directly.
	 */
	@Override
	public void onCreate() {
		// instantiate system services
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		// instantiate private fields
		mMessenger = new Messenger(new IncomingHandler(this));
		sensorMap = new HashMap<Integer, BufferedWriter>();

		/**
		 * Acquire a partial wakelock in order to allow for sensor event logging
		 * when the user presses the power button. see Android documentation: If
		 * you hold a partial wakelock, the CPU will continue to run,
		 * irrespective of any timers and even after the user presses the power
		 * button. In all other wakelocks, the CPU will run, but the user can
		 * still put the device to sleep using the power button.
		 */
		final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		partialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		synchronized (this) {
			partialWakeLock.acquire();
		}
	}

	@Override
	public void onDestroy() {
		// release the wake lock
		synchronized (this) {
			partialWakeLock.release();
		}
	}

	/**
	 * Displays a notification as long as this service is logging sensor values.
	 * The user will be redirected to the logging activity if he reacts to the
	 * notification.
	 */
	private void showNotification() {
		// Text being displayed for the ticker and expanded notification
		final CharSequence notificationTitle = getText(R.string.logger_notification_title);
		final CharSequence notificationText = getText(R.string.logger_notification_text);

		final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
		builder.setSmallIcon(R.drawable.ic_launcher);
		builder.setContentTitle(notificationTitle);
		builder.setContentText(notificationText);
		builder.setContentInfo(currentActivity);

		// the PendingIntent used to launch the logger activity if the user
		// selects this notification
		final Intent pendingIntent = new Intent(this, LoggerActivity.class);

		final TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
		stackBuilder.addParentStack(LoggerActivity.class);
		stackBuilder.addNextIntent(pendingIntent);

		final PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(resultPendingIntent);

		final Notification notification = builder.build();
		notification.flags |= Notification.FLAG_NO_CLEAR;
		notification.flags |= Notification.FLAG_ONGOING_EVENT;

		// send the notification. string id used because its a unique number.
		mNotificationManager.notify(R.string.logger_notification_title, notification);
	}

	/* **************************************************************************
	 * Service life-cycle
	 */

	private void startLogging(final Bundle data) throws IOException {
		currentActivity = data.getString(DATA_ACTIVITY_LABEL);
		currentActivityId = data.getInt(DATA_ACTIVITY_ID);
		referenceTime = SystemClock.uptimeMillis();
		startTime = new Date();

		final int linearAccSensor = Sensor.TYPE_LINEAR_ACCELERATION;
		final int gravitySensor = Sensor.TYPE_GRAVITY;
		sensorMap.put(linearAccSensor, new BufferedWriter(new FileWriter(getLogFile(linearAccSensor))));
		sensorMap.put(gravitySensor, new BufferedWriter(new FileWriter(getLogFile(gravitySensor))));

		for (final BufferedWriter writer : sensorMap.values()) {
			writer.write("#" + currentActivity.toLowerCase().replace(" ", "") + "\n");
			writer.write("time[ms], x-axis[m/s^2], y-axis[m/s^2], z-axis[m/s^2]\n");
		}

		mSensorManager.registerListener(sensorEventListener, mSensorManager.getSensorList(linearAccSensor).get(0),
				SensorManager.SENSOR_DELAY_FASTEST);
		mSensorManager.registerListener(sensorEventListener, mSensorManager.getSensorList(gravitySensor).get(0),
				SensorManager.SENSOR_DELAY_FASTEST);

		state = ServiceState.LOGGING;

		showNotification();
	}

	private void stopLogging() {
		state = ServiceState.STOPPED;

		// first stop logging new values
		mSensorManager.unregisterListener(sensorEventListener);

		// write files
		for (final BufferedWriter writer : sensorMap.values()) {
			try {
				writer.flush();
				writer.close();
			} catch (final IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}

		// remove notification (using the unique id of our string that
		// was used when we created the notification)
		mNotificationManager.cancel(R.string.logger_notification_title);
	}

	/* ************************************************************************* */

	/**
	 * Handler of incoming messages from clients.
	 */
	static class IncomingHandler extends Handler {

		private final WeakReference<SensorLoggerService> mService;

		public IncomingHandler(final SensorLoggerService service) {
			mService = new WeakReference<SensorLoggerService>(service);
		}

		@Override
		public void handleMessage(final Message msg) {
			final SensorLoggerService service = mService.get();

			switch (msg.what) {
			case MSG_REGISTER_CLIENT:
				// register the client
				service.mClient = msg.replyTo;
				break;
			case MSG_START_LOGGING:
				if (service.state == ServiceState.LOGGING && msg.getData().getInt(DATA_ACTIVITY_ID) == service.currentActivityId) {
					service.stopLogging();
				} else {
					if (service.state == ServiceState.LOGGING) {
						service.stopLogging();
					}
					try {
						service.startLogging(msg.getData());
					} catch (final IOException e) {
						Log.e(TAG, e.getMessage(), e);
					}
				}
				break;
			case MSG_STOP_LOGGING:
				if (service.state == ServiceState.LOGGING) {
					service.stopLogging();
					Toast.makeText(service.getApplicationContext(), R.string.toast_logging_stopped, Toast.LENGTH_SHORT).show();
				}
				break;
			}

			service.informClientCurrentStatus();
		}
	}

	public enum ServiceState {

		/**
		 * The service is in state logging iff the sensor event listener is
		 * registered.
		 */
		LOGGING,
		/**
		 * The service starts in this state. No logging is going on in this
		 * state.
		 */
		STOPPED;
	}

}
