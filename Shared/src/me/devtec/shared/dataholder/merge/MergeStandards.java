package me.devtec.shared.dataholder.merge;

import java.util.Iterator;
import java.util.Map.Entry;

import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;

public class MergeStandards {
	public static MergeSetting ADD_MISSING_HEADER = new MergeSetting() {

		@Override
		public boolean merge(Config config, Config merge) {
			boolean change = false;
			try {
				if (config.getDataLoader().getHeader() != null /** Is header supported? **/
						&& merge.getDataLoader().getHeader() != null && !merge.getDataLoader().getHeader().isEmpty() && config.getDataLoader().getHeader().isEmpty()) {
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
			boolean change = false;
			try {
				Iterator<Entry<String, DataValue>> iterator = merge.getDataLoader().get().entrySet().iterator();
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
			boolean change = false;
			try {
				Iterator<Entry<String, DataValue>> iterator = merge.getDataLoader().get().entrySet().iterator();
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
}
