package at.jku.tfeichtinger.sensorlogger.activities;

import java.io.File;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import at.jku.tfeichtinger.sensorlogger.R;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class FilesFragment extends SherlockFragment {

	/* ********************************************************************
	 * Fields
	 */
	private File[] allFiles;

	/* ********************************************************************
	 * UI stuff
	 */
	private ListView filesListView;
	private FilesAdapter filesAdapter;

	private ActionMode mMode;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.fragment_files, container, false);

		filesListView = (ListView) view.findViewById(R.id.filesList);
		filesListView.setItemsCanFocus(false);

		filesListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		filesListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				SparseBooleanArray checked = filesListView.getCheckedItemPositions();
				boolean hasCheckedElement = false;
				for (int i = 0; i < checked.size() && !hasCheckedElement; i++) {
					hasCheckedElement = checked.valueAt(i);
				}

				if (hasCheckedElement) {
					if (mMode == null) {
						mMode = getSherlockActivity().startActionMode(new ModeCallback());
					}
				} else {
					if (mMode != null) {
						mMode.finish();
					}
				}
			}

		});

		filesAdapter = new FilesAdapter(getActivity());
		filesListView.setAdapter(filesAdapter);
		return view;
	}

	private final class ModeCallback implements ActionMode.Callback {

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			// Create the menu from the xml file
			MenuInflater inflater = getSherlockActivity().getSupportMenuInflater();
			inflater.inflate(R.menu.files_context, menu);
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			// Here, you can checked selected items to adapt available actions
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			// Destroying action mode, let's unselect all items
			for (int i = 0; i < filesListView.getAdapter().getCount(); i++)
				filesListView.setItemChecked(i, false);

			if (mode == mMode) {
				mMode = null;
			}
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			// Respond to clicks on the actions in the CAB
			switch (item.getItemId()) {
			case R.id.menu_delete:
				deleteSelectedLogs();
				mode.finish(); // Action picked, so close the CAB
				return true;
			case R.id.menu_selectall:
				selectAll();
				return true;
			case R.id.menu_share:
				shareSelectedLogs();
				return true;
			default:
				return false;
			}

			// long[] selected = filesListView.getCheckedItemIds();
			// if (selected.length > 0) {
			// for (long id : selected) {
			// // Do something with the selected item
			// }
			// }
			// mode.finish();
		}
	};

	@Override
	public void onResume() {
		super.onResume();
		updateFilesList();
	}

	private void updateFilesList() {
		allFiles = getSherlockActivity().getExternalFilesDir(null).listFiles();
		filesAdapter.clear();

		// add all files; addAll requires API level > 11
		for (File f : allFiles) {
			filesAdapter.add(f);
		}
	}

	private void deleteSelectedLogs() {
		final int len = filesListView.getCount();
		for (int i = 0; i < len; i++) {
			final SparseBooleanArray checked = filesListView.getCheckedItemPositions();
			if (checked.get(i)) {
				File file = allFiles[i];

				// actually this should always be a directory...
				if (file.isDirectory()) {
					for (final File f : file.listFiles()) {
						f.delete();
					}
				}
				file.delete();
			}
		}
		updateFilesList();
	}

	private void shareSelectedLogs() {
		final ArrayList<Uri> paths = new ArrayList<Uri>();
		final int len = filesListView.getCount();
		final SparseBooleanArray checked = filesListView.getCheckedItemPositions();
		for (int i = 0; i < len; i++) {
			if (checked.get(i)) {
				for (File f : allFiles[i].listFiles()) {
					paths.add(Uri.parse("file://" + f.getAbsolutePath()));
				}
			}
		}

		final Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND_MULTIPLE);
		sharingIntent.setType("text/plain");
		sharingIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, paths);
		startActivity(Intent.createChooser(sharingIntent, "Share log files to..."));
	}

	private void selectAll() {
		for (int position = 0; position < filesAdapter.getCount(); position++) {
			filesListView.setItemChecked(position, true);
		}
	}

	private long getDirectorySize(File dir) {
		if (!dir.isDirectory()) {
			return 0;
		}

		long size = 0;
		for (File f : dir.listFiles()) {
			size += f.length();
		}

		return size;
	}

	public String readableFileSize(long size) {
		if (size <= 0)
			return "0";
		final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
		int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
		return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
	}

	/** The list adapter. */
	private class FilesAdapter extends ArrayAdapter<File> {

		public FilesAdapter(final Context context) {
			super(context, R.layout.file_list_item);
		}

		@Override
		public View getView(final int position, View convertView, final ViewGroup parent) {
			if (convertView == null) {
				final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				convertView = inflater.inflate(R.layout.file_list_item, null);
			}

			final File f = getItem(position);
			if (f != null) {
				// show the files name
				final TextView nameView = (TextView) convertView.findViewById(R.id.filelistitem_file_name);
				if (nameView != null) {
					nameView.setText(f.getName().split("-")[2]);
				}

				// show log size
				final TextView sizeView = (TextView) convertView.findViewById(R.id.filelistitem_file_size);
				if (sizeView != null) {
					sizeView.setText(readableFileSize(getDirectorySize(f)));
				}

				// show a the creation date
				final TextView dateView = (TextView) convertView.findViewById(R.id.filelistitem_file_date);
				if (dateView != null) {
					final DateFormat dateFormat = new SimpleDateFormat("dd.MM.yy HH:mm:ss");
					final String date = dateFormat.format(new Date(f.lastModified()));
					dateView.setText(date);
				}
			}
			return convertView;
		}
	}

}
