package at.jku.tfeichtinger.sensorlogger.entities;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class ActivityLabel {

	@DatabaseField(generatedId = true)
	private int id;

	@DatabaseField
	private String label;

	public ActivityLabel() {
		// needed by ormlite
	}

	public ActivityLabel(final String line) {
		this.label = line;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(final String label) {
		this.label = label;
	}

	public int getId() {
		return id;
	}

	@Override
	public String toString() {
		return "ActivityLabel [id=" + id + ", label=" + label + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		result = prime * result + ((label == null) ? 0 : label.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final ActivityLabel other = (ActivityLabel) obj;
		if (id != other.id)
			return false;
		if (label == null) {
			if (other.label != null)
				return false;
		} else if (!label.equals(other.label))
			return false;
		return true;
	}

}
