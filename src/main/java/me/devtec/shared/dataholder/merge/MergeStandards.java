package me.devtec.shared.dataholder.merge;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;

public class MergeStandards {
	public static final MergeSetting ADD_MISSING_HEADER = new MergeSetting() {

		@Override
		public boolean merge(Config config, Config merge) {
			Checkers.nonNull(config, "Primary Config");
			Checkers.nonNull(merge, "Merging Config");
			boolean change = false;
			try {
				Iterator<Entry<String, DataValue>> itr;
				if (config.getDataLoader().getHeader() != null
                        && merge.getDataLoader().getHeader() != null && !merge.getDataLoader().getHeader().isEmpty() && config.getDataLoader().getHeader().isEmpty()
						&& (itr = config.getDataLoader().entrySet().iterator()).hasNext() && itr.next().getValue().comments == null) {
					config.getDataLoader().getHeader().clear();
					config.getDataLoader().getHeader().addAll(merge.getDataLoader().getHeader());
					change = true;
				}
			} catch (Exception ignored) {
			}
			return change;
		}
	};
	public static final MergeSetting ADD_MISSING_FOOTER = new MergeSetting() {

		@Override
		public boolean merge(Config config, Config merge) {
			Checkers.nonNull(config, "Primary Config");
			Checkers.nonNull(merge, "Merging Config");
			boolean change = false;
			try {
				if (config.getDataLoader().getFooter() != null
                        && merge.getDataLoader().getFooter() != null && !merge.getDataLoader().getFooter().isEmpty() && config.getDataLoader().getFooter().isEmpty()) {
					config.getDataLoader().getFooter().clear();
					config.getDataLoader().getFooter().addAll(merge.getDataLoader().getFooter());
					change = true;
				}
			} catch (Exception ignored) {
			}
			return change;
		}
	};
	public static final MergeSetting ADD_MISSING_COMMENTS = new MergeSetting() {

		@Override
		public boolean merge(Config config, Config merge) {
			Checkers.nonNull(config, "Primary Config");
			Checkers.nonNull(merge, "Merging Config");
			boolean change = false;
			try {
                for (Entry<String, DataValue> key : merge.getDataLoader().entrySet()) {
					change |= mergeDataComments(config, key);
                }
			} catch (Exception ignored) {
			}
			return change;
		}
	};
	public static final MergeSetting ADD_MISSING_KEYS = new MergeSetting() {

		@Override
		public boolean merge(Config config, Config merge) {
			Checkers.nonNull(config, "Primary Config");
			Checkers.nonNull(merge, "Merging Config");
			boolean change = false;
			try {
                for (Entry<String, DataValue> key : merge.getDataLoader().entrySet()) {
					change |= mergeDataValue(config, key);
                }
			} catch (Exception ignored) {
			}
			return change;
		}
	};
	public static final MergeSetting[] DEFAULT = { ADD_MISSING_KEYS, ADD_MISSING_HEADER, ADD_MISSING_FOOTER, ADD_MISSING_COMMENTS };

	public static MergeSetting[] ignoreSections(List<String> sections) {
		if (sections == null || sections.isEmpty()) {
			return DEFAULT;
		}
		return ignoreSections(sections.toArray(new String[0]));
	}

	public static MergeSetting[] ignoreSections(String... sections) {
		if (sections == null || sections.length == 0) {
			return DEFAULT;
		}
		MergeSetting[] merge = new MergeSetting[4];
		merge[0] = new MergeSetting() {

			@Override
			public boolean merge(Config config, Config merge) {
				Checkers.nonNull(config, "Primary Config");
				Checkers.nonNull(merge, "Merging Config");
				boolean change = false;
				try {
					Iterator<Entry<String, DataValue>> iterator = merge.getDataLoader().entrySet().iterator();
					loop: while (iterator.hasNext()) {
						Entry<String, DataValue> key = iterator.next();
						for (String section : sections) {
							if (key.getKey().startsWith(section) && (key.getKey().length() == section.length() || key.getKey().charAt(section.length()) == '.')) {
								continue loop;
							}
						}
						change |= mergeDataValue(config, key);
					}
				} catch (Exception ignored) {
				}
				return change;
			}
		};
		merge[1] = new MergeSetting() {

			@Override
			public boolean merge(Config config, Config merge) {
				Checkers.nonNull(config, "Primary Config");
				Checkers.nonNull(merge, "Merging Config");
				boolean change = false;
				try {
					Iterator<Entry<String, DataValue>> iterator = merge.getDataLoader().entrySet().iterator();
					loop: while (iterator.hasNext()) {
						Entry<String, DataValue> key = iterator.next();
						for (String section : sections) {
							if (key.getKey().startsWith(section) && (key.getKey().length() == section.length() || key.getKey().charAt(section.length()) == '.')) {
								continue loop;
							}
						}
						change |= mergeDataComments(config, key);
					}
				} catch (Exception ignored) {
				}
				return change;
			}
		};
		merge[2] = ADD_MISSING_HEADER;
		merge[3] = ADD_MISSING_FOOTER;
		return merge;
	}

	private static boolean mergeDataValue(Config config, Entry<String, DataValue> key) {
		DataValue val = key.getValue();
		DataValue configVal = config.getDataLoader().get(key.getKey());
		if (configVal == null || configVal.value == null) {
			if (configVal == null) {
				configVal = config.getDataLoader().getOrCreate(key.getKey());
			}
			configVal.value = val.value;
			configVal.writtenValue = val.writtenValue;
			configVal.modified = true;
			return true;
		}
		return false;
	}

	private static boolean mergeDataComments(Config config, Entry<String, DataValue> key){
		DataValue val = key.getValue();
		DataValue configVal = null;
		boolean change = false;
		if (val.commentAfterValue != null) {
			configVal = config.getDataLoader().getOrCreate(key.getKey());
			if (configVal.commentAfterValue == null) {
				configVal.commentAfterValue = val.commentAfterValue;
				configVal.modified = true;
				change = true;
			}
		}
		if (val.comments != null && !val.comments.isEmpty()) {
			if (configVal == null) {
				configVal = config.getDataLoader().getOrCreate(key.getKey());
			}
			if (configVal.comments == null || configVal.comments.isEmpty()) {
				configVal.comments = val.comments;
				configVal.modified = true;
				change = true;
			}
		}
		return change;
	}
}
