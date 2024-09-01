package me.devtec.shared.dataholder.cache;

import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("serial")
public final class ConcurrentSet<E> extends AbstractSet<E> implements Serializable {

	private final ConcurrentMap<E, Boolean> map;

	public ConcurrentSet() {
		map = new ConcurrentHashMap<>();
	}

	public ConcurrentSet(int size) {
		map = new ConcurrentHashMap<>(size);
	}

	public ConcurrentSet(Collection<? extends E> collection) {
		this(collection.size());
		addAll(collection);
	}

	@Override
	public int size() {
		return this.map.size();
	}

	@Override
	public boolean contains(Object o) {
		return this.map.containsKey(o);
	}

	@Override
	public boolean add(E o) {
		return this.map.putIfAbsent(o, Boolean.TRUE) == null;
	}

	@Override
	public boolean remove(Object o) {
		return this.map.remove(o) != null;
	}

	@Override
	public void clear() {
		this.map.clear();
	}

	@Override
	public Iterator<E> iterator() {
		return this.map.keySet().iterator();
	}
}