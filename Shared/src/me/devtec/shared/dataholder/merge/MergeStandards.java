package me.devtec.shared.dataholder.merge;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;

public class MergeStandards {
	public static MergeSetting ADD_MISSING_HEADER = new MergeSetting() {

		@Override
		public boolean merge(Config config, Config merge) {
			Checkers.nonNull(config, "Primary Config");
			Checkers.nonNull(merge, "Merging Config");
			boolean change = false;
			try {
				Iterator<Entry<String, DataValue>> itr;
				if (config.getDataLoader().getHeader() != null /** Is header supported? **/
						&& merge.getDataLoader().getHeader() != null && !merge.getDataLoader().getHeader().isEmpty() && config.getDataLoader().getHeader().isEmpty()
						&& (itr = config.getDataLoader().entrySet().iterator()).hasNext() && itr.next().getValue().comments == null) {
					config.getDataLoader().getHeader().clear();
					config.getDataLoader().getHeader().addAll(merge.getDataLoader().getHeader());
					change = true;
				}
			} catch (Exception error) {
			}
			return change;
		}
	};
	public static MergeSetting ADD_MISSING_FOOTER = new MergeSetting() {

		@Override
		public boolean merge(Config config, Config merge) {
			Checkers.nonNull(config, "Primary Config");
			Checkers.nonNull(merge, "Merging Config");
			boolean change = false;
			try {
				if (config.getDataLoader().getFooter() != null /** Is footer supported? **/
						&& merge.getDataLoader().getFooter() != null && !merge.getDataLoader().getFooter().isEmpty() && config.getDataLoader().getFooter().isEmpty()) {
					config.getDataLoader().getFooter().clear();
					config.getDataLoader().getFooter().addAll(merge.getDataLoader().getFooter());
					change = true;
				}
			} catch (Exception error) {
			}
			return change;
		}
	};
	public static MergeSetting ADD_MISSING_COMMENTS = new MergeSetting() {

		@Override
		public boolean merge(Config config, Config merge) {
			Checkers.nonNull(config, "Primary Config");
			Checkers.nonNull(merge, "Merging Config");
			boolean change = false;
			try {
				Iterator<Entry<String, DataValue>> iterator = merge.getDataLoader().entrySet().iterator();
				while (iterator.hasNext()) {
					Entry<String, DataValue> key = iterator.next();
					DataValue val = key.getValue();
					DataValue configVal = null;
					if (val.commentAfterValue != null) {
						configVal = config.getDataLoader().getOrCreate(key.getKey());
						if (configVal.commentAfterValue == null) {
							configVal.commentAfterValue = val.commentAfterValue;
							configVal.modified = true;
							change = true;
						}
					}
					if (val.comments != null && !val.comments.isEmpty()) {
						if (configVal == null)
							configVal = config.getDataLoader().getOrCreate(key.getKey());
						if (configVal.comments == null || configVal.comments.isEmpty()) {
							configVal.comments = val.comments;
							configVal.modified = true;
							change = true;
						}
					}
				}
			} catch (Exception err) {
			}
			return change;
		}
	};
	public static MergeSetting ADD_MISSING_KEYS = new MergeSetting() {

		@Override
		public boolean merge(Config config, Config merge) {
			Checkers.nonNull(config, "Primary Config");
			Checkers.nonNull(merge, "Merging Config");
			boolean change = false;
			try {
				Iterator<Entry<String, DataValue>> iterator = merge.getDataLoader().entrySet().iterator();
				while (iterator.hasNext()) {
					Entry<String, DataValue> key = iterator.next();
					DataValue val = key.getValue();
					DataValue configVal = config.getDataLoader().get(key.getKey());
					if (configVal == null || configVal.value == null) {
						if (configVal == null)
							configVal = config.getDataLoader().getOrCreate(key.getKey());
						configVal.value = val.value;
						configVal.writtenValue = val.writtenValue;
						configVal.modified = true;
						change = true;
					}
				}
			} catch (Exception err) {
			}
			return change;
		}
	};
	public static MergeSetting[] DEFAULT = { ADD_MISSING_KEYS, ADD_MISSING_HEADER, ADD_MISSING_FOOTER, ADD_MISSING_COMMENTS };

	public static MergeSetting[] ignoreSections(List<String> sections) {
		if (sections == null || sections.isEmpty())
			return DEFAULT;
		return ignoreSections(sections.toArray(new String[sections.size()]));
	}

	public static MergeSetting[] ignoreSections(String... sections) {
		if (sections == null || sections.length == 0)
			return DEFAULT;
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
						for (String section : sections)
							if (key.getKey().startsWith(section) && (key.getKey().length() == section.length() || key.getKey().charAt(section.length()) == '.'))
								continue loop;
						DataValue val = key.getValue();
						DataValue configVal = config.getDataLoader().get(key.getKey());
						if (configVal == null || configVal.value == null) {
							if (configVal == null)
								configVal = config.getDataLoader().getOrCreate(key.getKey());
							configVal.value = val.value;
							configVal.writtenValue = val.writtenValue;
							configVal.modified = true;
							change = true;
						}
					}
				} catch (Exception err) {
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
						for (String section : sections)
							if (key.getKey().startsWith(section) && (key.getKey().length() == section.length() || key.getKey().charAt(section.length()) == '.'))
								continue loop;
						DataValue val = key.getValue();
						DataValue configVal = null;
						if (val.commentAfterValue != null) {
							configVal = config.getDataLoader().getOrCreate(key.getKey());
							if (configVal.commentAfterValue == null) {
								configVal.commentAfterValue = val.commentAfterValue;
								configVal.modified = true;
								change = true;
							}
						}
						if (val.comments != null && !val.comments.isEmpty()) {
							if (configVal == null)
								configVal = config.getDataLoader().getOrCreate(key.getKey());
							if (configVal.comments == null || configVal.comments.isEmpty()) {
								configVal.comments = val.comments;
								configVal.modified = true;
								change = true;
							}
						}
					}
				} catch (Exception err) {
				}
				return change;
			}
		};
		merge[2] = ADD_MISSING_HEADER;
		merge[3] = ADD_MISSING_FOOTER;
		return merge;
	}
}
