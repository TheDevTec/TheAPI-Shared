package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import me.devtec.shared.Ref;
import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.cache.ConcurrentLinkedHashMap;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;

public class EmptyLoader extends DataLoader {
	protected Map<String, DataValue> data = new ConcurrentLinkedHashMap<>();
	protected Set<String> primaryKeys = new LinkedHashSet<>();
	protected List<String> header = new ArrayList<>();
	protected List<String> footer = new ArrayList<>();
	protected boolean loaded = false;

	protected Set<String> keySet;
	protected Set<Entry<String, DataValue>> entrySet;

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
		if (keySet == null)
			keySet = data.keySet();
		return keySet;
	}

	@Override
	public Set<Entry<String, DataValue>> entrySet() {
		if (entrySet == null)
			entrySet = data.entrySet();
		return entrySet;
	}

	@Override
	public DataValue get(String key) {
		Checkers.nonNull(key, "Key");
		return data.get(key);
	}

	@Override
	public DataValue getOrCreate(String key) {
		Checkers.nonNull(key, "Key");
		DataValue v = get(key);
		if (v == null)
			set(key, v = DataValue.empty());
		return v;
	}

	@Override
	public void set(String key, DataValue holder) {
		Checkers.nonNull(key, "Key");
		Checkers.nonNull(holder, "DataValue");
		if (data.put(key, holder) == null) {
			int pos = key.indexOf('.');
			String primaryKey = pos == -1 ? key : key.substring(0, pos);
			primaryKeys.add(primaryKey);
			keySet = null;
			entrySet = null;
		}
	}

	@Override
	public boolean remove(String key, boolean withSubKeys) {
		Checkers.nonNull(key, "Key");
		if (withSubKeys) {
			int pos = key.indexOf('.');
			String primaryKey = pos == -1 ? key : key.substring(0, pos);
			if (pos == -1) {
				boolean modified = primaryKeys.remove(primaryKey);
                if (data.remove(key) != null)
					modified = true;
				key += '.';
				Iterator<Entry<String, DataValue>> itr = entrySet().iterator();
				while (itr.hasNext()) {
					Entry<String, DataValue> section = itr.next();
					if (section.getKey().startsWith(key)) {
						itr.remove();
						modified = true;
					}
				}
				if (modified) {
					keySet = null;
					entrySet = null;
				}
				return modified;
			}
			boolean onlyOne = true;
			boolean modified = data.remove(key) != null;
            key += '.';
			Iterator<Entry<String, DataValue>> itr = entrySet().iterator();
			while (itr.hasNext()) {
				Entry<String, DataValue> section = itr.next();
				if (section.getKey().startsWith(key)) {
					itr.remove();
					modified = true;
				} else if (section.getKey().startsWith(primaryKey) && (section.getKey().length() == primaryKey.length() || section.getKey().charAt(primaryKey.length()) == '.'))
					onlyOne = false;
			}
			if (onlyOne && primaryKeys.remove(primaryKey))
				modified = true;
			if (modified) {
				keySet = null;
				entrySet = null;
			}
			return modified;
		}
		if (data.remove(key) != null) {
			int pos = key.indexOf('.');
			String primaryKey = pos == -1 ? key : key.substring(0, pos);
			for (String section : getKeys())
				if (section.startsWith(primaryKey) && (section.length() == primaryKey.length() || section.charAt(primaryKey.length()) == '.')) {
					keySet = null;
					entrySet = null;
					return true;
				}
			primaryKeys.remove(primaryKey);
			keySet = null;
			entrySet = null;
			return true;
		}
		return false;
	}

	@Override
	public void reset() {
		keySet = null;
		entrySet = null;
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
		EmptyLoader clone = (EmptyLoader) Ref.newInstanceByClass(getClass());
		clone.data = new LinkedHashMap<>(data);
		clone.primaryKeys = new LinkedHashSet<>(primaryKeys);
		clone.footer = new ArrayList<>(footer);
		clone.header = new ArrayList<>(header);
		clone.loaded = loaded;
		return clone;
	}

	@Override
	public Set<String> keySet(String key, boolean subkeys) {
		Checkers.nonNull(key, "Key");
		Set<String> keys = new LinkedHashSet<>();
		key = key + '.';
		for (String section : getKeys())
			if (section.startsWith(key)) {
				int pos;
				section = section.substring(key.length());
				keys.add(subkeys ? section : (pos = section.indexOf('.')) == -1 ? section : section.substring(0, pos));
			}
		return keys;
	}

	@Override
	public Iterator<String> keySetIterator(String key, boolean subkeys) {
		Checkers.nonNull(key, "Key");
		String finalKey = key + '.';
		return new Iterator<String>() {
			String currentKey = null;

			@Override
			public boolean hasNext() {
				return currentKey != null;
			}

			@Override
			public String next() {
				step();
				return currentKey;
			}

			@Override
			public void remove() {
				EmptyLoader.this.remove(currentKey);
			}

			public Iterator<String> step() {
				currentKey = null;
				for (String section : getKeys())
					if (section.startsWith(finalKey)) {
						int pos;
						section = section.substring(finalKey.length());
						currentKey = subkeys ? section : (pos = section.indexOf('.')) == -1 ? section : section.substring(0, pos);
						break;
					}
				return this;
			}
		}.step();
	}

	@Override
	public byte[] save(Config config, boolean markSaved) {
		throw new UnsupportedOperationException("Method isn't implemented");
	}

	@Override
	public String saveAsString(Config config, boolean markSaved) {
		throw new UnsupportedOperationException("Method isn't implemented");
	}

	@Override
	public String name() {
		return "empty";
	}
}
