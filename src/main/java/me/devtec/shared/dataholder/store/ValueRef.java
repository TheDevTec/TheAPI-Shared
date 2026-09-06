package me.devtec.shared.dataholder.store;

/**
 * Logical, source-format independent value. Disk references decode only on
 * demand.
 */
public interface ValueRef {
	Object get();

	long estimatedHeap();

	default void writeJson(java.io.Writer writer) throws java.io.IOException {
		me.devtec.shared.dataholder.codec.ConfigWriter.jsonValue(get(), writer, 0, null);
	}

	default boolean largeText() {
		return false;
	}

	default boolean requiresExternalValue() {
		return false;
	}

	final class Memory implements ValueRef {
		private final Object value;

		public Memory(Object value) {
			this.value = value;
		}

		@Override
		public Object get() {
			return value;
		}

		@Override
		public long estimatedHeap() {
			return MemoryEstimator.estimate(value);
		}
	}
}
