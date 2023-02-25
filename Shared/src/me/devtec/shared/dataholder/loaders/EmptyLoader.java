package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.devtec.shared.dataholder.loaders.constructor.DataValue;

public class EmptyLoader extends DataLoader {
	protected Map<String, DataValue> data = new LinkedHashMap<>();
	protected Set<String> primaryKeys = new LinkedHashSet<>();
	protected List<String> header = new ArrayList<>();
	protected List<String> footer = new ArrayList<>();
	protected boolean loaded = false;

	@Override
	public boolean loadingFromFile() {
		return false;
	}

	@Override
	public Map<String, DataValue> get() {
		return data;
	}

	@Override
	public Set<String> getPrimaryKeys() {
		return primaryKeys;
	}

	@Override
	public Set<String> getKeys() {
		return data.keySet();
	}

	@Override
	public void set(String key, DataValue holder) {
		if (key == null)
			return;
		if (holder == null) {
			data.remove(key);
			return;
		}
		data.put(key, holder);
	}

	@Override
	public boolean remove(String key) {
		if (key == null)
			return false;
		return data.remove(key) != null;
	}

	@Override
	public void reset() {
		data.clear();
		header.clear();
		footer.clear();
		primaryKeys.clear();
		loaded = false;
	}

	@Override
	public void load(String input) {
		reset();
		loaded = true;
	}

	@Override
	public Collection<String> getHeader() {
		return header;
	}

	@Override
	public Collection<String> getFooter() {
		return footer;
	}

	@Override
	public boolean isLoaded() {
		return loaded;
	}

	@Override
	public DataLoader clone() {
		try {
			EmptyLoader clone = getClass().newInstance();
			clone.data = new LinkedHashMap<>(data);
			clone.footer = new ArrayList<>(footer);
			clone.header = new ArrayList<>(header);
			clone.loaded = loaded;
			return clone;
		} catch (Exception e) {
		}
		return null;
	}
}
