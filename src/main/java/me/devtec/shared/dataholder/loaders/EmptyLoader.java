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
import me.devtec.shared.dataholder.StringContainer;
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
		boolean modified = false;

		if (withSubKeys) {
			int dotPos = key.indexOf('.');
			String primaryKey = dotPos == -1 ? key : key.substring(0, dotPos);
			String prefix = key + '.';

			// Odstranit hlavní klíč a podklíče
			if (dotPos == -1) {
				// Odstranit primární klíč
				modified = primaryKeys.remove(primaryKey);
				// Odstranit hlavní klíč
				modified |= data.remove(key) != null;

				// Použít přímý iterator nad entrySet pro odstranění podklíčů
				Iterator<Map.Entry<String, DataValue>> iterator = data.entrySet().iterator();
				while (iterator.hasNext()) {
					Map.Entry<String, DataValue> entry = iterator.next();
					String entryKey = entry.getKey();
					if (entryKey.startsWith(prefix)) {
						iterator.remove();
						modified = true;
					}
				}
			} else {
				// Odstranit konkrétní klíč
				modified = data.remove(key) != null;

				// Odstranit podklíče
				Iterator<Map.Entry<String, DataValue>> iterator = data.entrySet().iterator();
				while (iterator.hasNext()) {
					Map.Entry<String, DataValue> entry = iterator.next();
					String entryKey = entry.getKey();
					if (entryKey.startsWith(prefix)) {
						iterator.remove();
						modified = true;
					}
				}

				// Zkontrolovat, zda zůstaly další klíče se stejným primárním klíčem
				boolean hasOtherPrimaryKeys = false;
				int primaryKeyLength = primaryKey.length();

				for (String existingKey : data.keySet())
					if (existingKey.startsWith(primaryKey) &&
							(existingKey.length() == primaryKeyLength ||
							existingKey.charAt(primaryKeyLength) == '.')) {
						hasOtherPrimaryKeys = true;
						break;
					}

				// Pokud nezůstaly žádné další klíče s tímto primárním klíčem, odstranit ho
				if (!hasOtherPrimaryKeys)
					primaryKeys.remove(primaryKey);
			}
		} else // Odstranit pouze konkrétní klíč
			if (data.remove(key) != null) {
				modified = true;

				int dotPos = key.indexOf('.');
				if (dotPos != -1) {
					// U vnořeného klíče - zkontrolovat primární klíč
					String primaryKey = key.substring(0, dotPos);
					boolean hasOtherKeys = false;
					int primaryKeyLength = primaryKey.length();

					for (String existingKey : data.keySet())
						if (existingKey.startsWith(primaryKey) &&
								(existingKey.length() == primaryKeyLength ||
								existingKey.charAt(primaryKeyLength) == '.')) {
							hasOtherKeys = true;
							break;
						}

					// Pokud nezůstaly žádné další klíče s tímto primárním klíčem, odstranit ho
					if (!hasOtherKeys)
						primaryKeys.remove(primaryKey);
				} else
					// Primární klíč - odstranit ho ze seznamu primárních klíčů
					primaryKeys.remove(key);
			}

		// Resetovat cached kolekce pokud došlo ke změně
		if (modified) {
			keySet = null;
			entrySet = null;
		}

		return modified;
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
	public void load(StringContainer container, List<int[]> input) {
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
		String prefix = key + '.';
		int prefixLength = prefix.length();

		for (String section : getKeys()) {
			if (!section.startsWith(prefix))
				continue;

			String remainder = section.substring(prefixLength);

			if (subkeys)
				keys.add(remainder);
			else {
				int dotIndex = remainder.indexOf('.');
				if (dotIndex == -1)
					keys.add(remainder);
				else
					keys.add(remainder.substring(0, dotIndex));
			}
		}
		return keys;
	}

	@Override
	public Iterator<String> keySetIterator(String key, boolean subkeys) {
		Checkers.nonNull(key, "Key");
		String prefix = key + '.';
		int prefixLength = prefix.length();

		return new Iterator<>() {
			String next = null;
			boolean hasPrefetched = false;

			private void fetchNext() {
				if (hasPrefetched)
					return;
				hasPrefetched = true;
				for (String section : getKeys()) {
					if (!section.startsWith(prefix))
						continue;
					String remainder = section.substring(prefixLength);
					int dotIndex = remainder.indexOf('.');
					String candidate = subkeys ? remainder
							: dotIndex == -1 ? remainder : remainder.substring(0, dotIndex);
					next = candidate;
					return;
				}
				next = null;
			}

			@Override
			public boolean hasNext() {
				fetchNext();
				return next != null;
			}

			@Override
			public String next() {
				fetchNext();
				if (next == null)
					return null;
				String result = next;
				next = null;
				hasPrefetched = false;
				return result;
			}

			@Override
			public void remove() {
				EmptyLoader.this.remove(next);
			}
		};
	}

	@Override
	public boolean supportsReadingLines() {
		return false;
	}

	@Override
	public String name() {
		return "empty";
	}
}
