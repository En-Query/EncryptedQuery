/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package org.enquery.encryptedquery.flink.streaming;

import java.io.IOException;
import java.text.MessageFormat;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.state.ReducingState;
import org.apache.flink.api.common.state.ReducingStateDescriptor;
import org.apache.flink.streaming.api.windowing.triggers.Trigger;
import org.apache.flink.streaming.api.windowing.triggers.TriggerResult;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.enquery.encryptedquery.flink.TimestampFormatter;
import org.enquery.encryptedquery.responder.QueueRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ExecutionTimeTrigger extends Trigger<QueueRecord, TimeWindow> {

	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(ExecutionTimeTrigger.class);

	private final Long maxTimestamp;
	private final String responseFilePath;
	private final int maxHitsPerSelector;
	private transient ReducingStateDescriptor<Integer> hitCounter;

	/**
	 * @param runtimeSeconds
	 */
	public ExecutionTimeTrigger(Long maxTimestamp,
			String responseFilePath,
			int maxHitsPerSelector) {

		Validate.notNull(responseFilePath);
		Validate.isTrue(maxHitsPerSelector > 0);

		this.maxTimestamp = maxTimestamp;
		this.responseFilePath = responseFilePath;
		this.maxHitsPerSelector = maxHitsPerSelector;

		log.info("Initialized with maxTimestamp: {}, responseFilePath: '{}', maxHitsPerSelector: {}",
				(maxTimestamp == null) ? "infinite" : TimestampFormatter.format(maxTimestamp),
				responseFilePath,
				maxHitsPerSelector);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.flink.streaming.api.windowing.triggers.Trigger#onElement(java.lang.Object,
	 * long, org.apache.flink.streaming.api.windowing.windows.Window,
	 * org.apache.flink.streaming.api.windowing.triggers.Trigger.TriggerContext)
	 */
	@Override
	public TriggerResult onElement(QueueRecord element, long time, TimeWindow window, TriggerContext ctx) throws Exception {

		final boolean debugging = log.isDebugEnabled();

		ReducingState<Integer> partitionedState = getCounter(ctx);
		partitionedState.add(1);
		int hitCount = partitionedState.get();

		final long start = window.getStart();
		final long end = window.maxTimestamp();

		String windowInfo = null;
		if (debugging) {
			windowInfo = MessageFormat.format(
					"[rowHash {0} from {1} to {2} count {3}]",
					element.getRowIndex(),
					TimestampFormatter.format(start),
					TimestampFormatter.format(end),
					hitCount);
		}

		// Discard any window that is started after maxTimestamp
		if (maxTimestamp != null && start > maxTimestamp) {
			if (debugging) log.debug("Window {} starts after maxTimestamp. Purging.", windowInfo);
			return TriggerResult.PURGE;
		}

		if (hitCount == maxHitsPerSelector) {
			if (debugging) log.debug("Window {} reached maxHitsPerSelector {}. Firing and purging.",
					windowInfo,
					maxHitsPerSelector);

			return TriggerResult.FIRE_AND_PURGE;
		} else if (hitCount > maxHitsPerSelector) {
			return TriggerResult.PURGE;
		} else if (hitCount <= 1) {
			createWindowProgressFile(start, end);
			// adjust the window trigger time to run on maxTimestamp if spans beyond maxTimestamp
			long triggerTime = window.maxTimestamp();
			if (maxTimestamp != null && triggerTime > maxTimestamp) {
				if (debugging) log.debug("Window maxTimestamp {} > {}. Trimming.",
						TimestampFormatter.format(triggerTime),
						TimestampFormatter.format(maxTimestamp));
				triggerTime = maxTimestamp;
			}
			ctx.registerProcessingTimeTimer(triggerTime);
			if (debugging) log.debug("Scheduled window {} to be processed at {}.",
					windowInfo,
					TimestampFormatter.format(triggerTime));
		}

		return TriggerResult.CONTINUE;
	}

	private ReducingState<Integer> getCounter(TriggerContext ctx) {
		if (hitCounter == null) {
			hitCounter = new ReducingStateDescriptor<>(
					"hitCount",
					(v1, v2) -> v1 + v2,
					Integer.class);
		}
		return ctx.getPartitionedState(hitCounter);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.flink.streaming.api.windowing.triggers.Trigger#onEventTime(long,
	 * org.apache.flink.streaming.api.windowing.windows.Window,
	 * org.apache.flink.streaming.api.windowing.triggers.Trigger.TriggerContext)
	 */
	@Override
	public TriggerResult onEventTime(long time, TimeWindow window, TriggerContext ctx) throws Exception {
		// if (log.isDebugEnabled()) log.debug("Received event time for {}.",
		// TimestampFormatter.format(time));
		return TriggerResult.PURGE;
	}

	private void createWindowProgressFile(long start, long end) throws IOException {
		boolean created = ResponseFileNameBuilder.createEmptyInProgressFile(responseFilePath, start, end);
		if (log.isDebugEnabled() && created) {
			log.debug("Created in-progress file for window from {} to {}.",
					TimestampFormatter.format(start),
					TimestampFormatter.format(end));
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.flink.streaming.api.windowing.triggers.Trigger#onProcessingTime(long,
	 * org.apache.flink.streaming.api.windowing.windows.Window,
	 * org.apache.flink.streaming.api.windowing.triggers.Trigger.TriggerContext)
	 */
	@Override
	public TriggerResult onProcessingTime(long time, TimeWindow window, TriggerContext ctx) throws Exception {
		final boolean debugging = log.isDebugEnabled();

		long start = window.getStart();
		long end = window.maxTimestamp();

		if (debugging) log.debug("Firing window from {} to {} due to time expiration.",
				TimestampFormatter.format(start),
				TimestampFormatter.format(end));

		return TriggerResult.FIRE_AND_PURGE;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.flink.streaming.api.windowing.triggers.Trigger#clear(org.apache.flink.streaming.
	 * api.windowing.windows.Window,
	 * org.apache.flink.streaming.api.windowing.triggers.Trigger.TriggerContext)
	 */
	@Override
	public void clear(TimeWindow window, TriggerContext ctx) throws Exception {
		ctx.deleteProcessingTimeTimer(window.maxTimestamp());
		if (hitCounter != null) {
			ctx.getPartitionedState(hitCounter).clear();
		}
	}

	@Override
	public boolean canMerge() {
		return true;
	}

	@Override
	public void onMerge(TimeWindow window, OnMergeContext ctx) {
		// only register a timer if the time is not yet past the end of the merged window
		// this is in line with the logic in onElement(). If the time is past the end of
		// the window onElement() will fire and setting a timer here would fire the window twice.

		long fireTimestamp = window.maxTimestamp();

		if (maxTimestamp != null) {
			if (fireTimestamp > maxTimestamp) {
				fireTimestamp = maxTimestamp;
			}
		}

		long currentTimestamp = ctx.getCurrentProcessingTime();
		if (currentTimestamp < fireTimestamp) {
			ctx.registerProcessingTimeTimer(fireTimestamp);
		}
	}

	@Override
	public String toString() {
		return "ExecutionTimeTrigger()";
	}

}
