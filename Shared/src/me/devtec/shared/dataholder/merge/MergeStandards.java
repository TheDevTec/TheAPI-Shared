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
						&& merge.getDataLoader().getHeader() != null && !merge.getDataLoader().getHeader().isEmpty()
						&& (config.getDataLoader().getHeader().isEmpty() || !config.getDataLoader().getHeader().containsAll(merge.getDataLoader().getHeader()))) {
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
						&& merge.getDataLoader().getFooter() != null && !merge.getDataLoader().getFooter().isEmpty()
						&& (config.getDataLoader().getFooter().isEmpty() || !config.getDataLoader().getFooter().containsAll(merge.getDataLoader().getFooter()))) {
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
					if (val.commentAfterValue == null ? key.getValue().commentAfterValue != null : !val.commentAfterValue.equals(key.getValue().commentAfterValue)) {
						val.commentAfterValue = key.getValue().commentAfterValue;
						change = true;
					}
					if (val.comments == null ? key.getValue().comments != null && !key.getValue().comments.isEmpty() : !val.comments.containsAll(key.getValue().comments)) {
						val.comments = key.getValue().comments;
						change = true;
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
					if (val.value == null && key.getValue().value != null) {
						val.value = key.getValue().value;
						val.writtenValue = key.getValue().writtenValue;
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
