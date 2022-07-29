package me.devtec.shared.dataholder.merge;

import java.util.List;
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
						&& merge.getDataLoader().getHeader() != null && !merge.getDataLoader().getHeader().isEmpty() && (config.getDataLoader().getHeader().isEmpty() || !config.getDataLoader().getHeader().containsAll(merge.getDataLoader().getHeader()))) {
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
						&& merge.getDataLoader().getFooter() != null && !merge.getDataLoader().getFooter().isEmpty() && (config.getDataLoader().getFooter().isEmpty() || !config.getDataLoader().getFooter().containsAll(merge.getDataLoader().getFooter()))) {
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
				boolean first = true;
				for (Entry<String, DataValue> s : merge.getDataLoader().get().entrySet()) {
					DataValue value = config.getData(s.getKey());
					if (value == null)
						continue;
					if (value.commentAfterValue == null && s.getValue().commentAfterValue != null && !s.getValue().commentAfterValue.equals(value.commentAfterValue)) {
						value.commentAfterValue = s.getValue().commentAfterValue;
						change = true;
					}
					if (s.getValue().comments != null && !s.getValue().comments.isEmpty()) {
						List<String> comments = value.comments;
						if (comments == null || comments.isEmpty()) {
							if (first && config.getHeader() != null && !config.getHeader().isEmpty() && config.getHeader().containsAll(s.getValue().comments))
								continue;
							value.comments = s.getValue().comments;
							change = true;
						}
					}
					first = false;
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
				for (Entry<String, DataValue> s : merge.getDataLoader().get().entrySet()) {
					DataValue value = config.getOrCreateData(s.getKey());
					if (value.value == null && s.getValue().value != null) {
						value.value = s.getValue().value;
						value.writtenValue = s.getValue().writtenValue;
						change = true;
					}
				}
			} catch (Exception err) {
			}
			return change;
		}
	};
	public static MergeSetting[] DEFAULT = { ADD_MISSING_KEYS, ADD_MISSING_COMMENTS, ADD_MISSING_HEADER, ADD_MISSING_FOOTER };
}
