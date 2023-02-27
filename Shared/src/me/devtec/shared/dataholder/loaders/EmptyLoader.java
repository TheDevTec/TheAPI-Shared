package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
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
	public DataValue get(String key) {
		return data.get(key);
	}

	@Override
	public DataValue getOrCreate(String key) {
		DataValue v = data.get(key);
		if (v == null)
			set(key, v = DataValue.empty());
		return v;
	}

	@Override
	public void set(String key, DataValue holder) {
		if (data.put(key, holder) == null) {
			int pos = key.indexOf('.');
			String primaryKey = pos == -1 ? key : key.substring(0, pos);
			primaryKeys.add(primaryKey);
		}
	}

	@Override
	public boolean remove(String key, boolean withSubKeys) {
		if (withSubKeys) {
			int pos = key.indexOf('.');
			String primaryKey = pos == -1 ? key : key.substring(0, pos);
			key = key + '.';
			if (pos == -1) {
				boolean modified = false;
				if (primaryKeys.remove(primaryKey))
					modified = true;
				if (data.remove(key) != null)
					modified = true;
				Iterator<String> itr = data.keySet().iterator();
				while (itr.hasNext()) {
					String section = itr.next();
					if (section.startsWith(key)) {
						itr.remove();
						modified = true;
					}
				}
				return modified;
			}
			boolean onlyOne = true;
			boolean modified = false;

			Iterator<String> itr = data.keySet().iterator();
			while (itr.hasNext()) {
				String section = itr.next();
				if (section.startsWith(key)) {
					itr.remove();
					modified = true;
				} else if (section.startsWith(primaryKey) && (section.length() == primaryKey.length() || section.charAt(primaryKey.length()) == '.'))
					onlyOne = false;
			}
			if (onlyOne && primaryKeys.remove(primaryKey))
				modified = true;
			return modified;
		}
		return data.remove(key) != null;
	}

	@Override
	public void reset() {
		primaryKeys.clear();
		data.clear();
		header.clear();
		footer.clear();
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
			clone.primaryKeys = new LinkedHashSet<>(primaryKeys);
			clone.footer = new ArrayList<>(footer);
			clone.header = new ArrayList<>(header);
			clone.loaded = loaded;
			return clone;
		} catch (Exception e) {
		}
		return null;
	}

	@Override
	public Set<String> keySet(String key, boolean subkeys) {
		Set<String> keys = new HashSet<>();
		key = key + '.';
		for (String section : data.keySet())
			if (section.startsWith(key)) {
				int pos;
				section = section.substring(key.length());
				keys.add(subkeys ? section : (pos = section.indexOf('.')) == -1 ? section : section.substring(0, pos));
			}
		return keys;
	}
}
