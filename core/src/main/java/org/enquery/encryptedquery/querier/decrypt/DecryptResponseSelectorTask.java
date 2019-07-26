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
package org.enquery.encryptedquery.querier.decrypt;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Hits;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Record;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.data.QuerySchemaElement;
import org.enquery.encryptedquery.data.RecordEncoding;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.PlainText;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decrypt a single selector
 *
 */
class DecryptResponseSelectorTask implements Callable<ClearTextQueryResponse.Selector> {
	private static final Logger log = LoggerFactory.getLogger(DecryptResponseSelectorTask.class);

	private final List<PlainText> rElements;
	private final QueryInfo queryInfo;
	private final Map<Integer, String> embedSelectorMap;
	private final String selectorValue;
	private final int selectorIndex;
	private final CryptoScheme crypto;

	private int falseHits = 0;
	private int goodHits = 0;

	public DecryptResponseSelectorTask(List<PlainText> rElements,
			String selectorValue,
			int selectorIndex,
			QueryInfo queryInfo,
			Map<Integer, String> embedSelectorMap,
			CryptoScheme crypto) {

		Validate.notNull(rElements, "rElements is null");
		Validate.notNull(selectorValue, "selectorValue is null");
		Validate.notNull(queryInfo, "queryInfo is null");
		Validate.notNull(embedSelectorMap, "embedSelectorMap is null");
		Validate.notNull(crypto);

		this.rElements = rElements;
		this.selectorValue = selectorValue;
		this.queryInfo = queryInfo;
		this.embedSelectorMap = embedSelectorMap;
		this.selectorIndex = selectorIndex;
		this.crypto = crypto;
	}

	@Override
	public ClearTextQueryResponse.Selector call() throws PIRException {

		final ClearTextQueryResponse.Selector result = new ClearTextQueryResponse.Selector(
				queryInfo.getQuerySchema().getSelectorField());

		ClearTextQueryResponse.Hits hits = new ClearTextQueryResponse.Hits(selectorValue);

		log.debug("Number of Columns = ( " + rElements.size() + " )");
		log.info("Processing selector value = {}", selectorValue);

		final List<byte[]> chunks = new ArrayList<>();
		int size = 0;
		for (PlainText pt : rElements) {
			final byte[] chunk = crypto.plainTextChunk(queryInfo, pt, selectorIndex);
			chunks.add(chunk);
			size += chunk.length;
		}

		ByteBuffer buffer = ByteBuffer.allocate(size);
		for (byte[] chunk : chunks) {
			buffer.put(chunk);
		}
		buffer.rewind();
		if (log.isDebugEnabled()) {
			log.debug("Assembled buffer from plain text with size : {}.", size);
		}

		RecordEncoding recordEncoding = new RecordEncoding(queryInfo);

		// process all record, skipping gaps between each record
		while (buffer.hasRemaining()) {

			skipGap(buffer);
			if (!buffer.hasRemaining()) break;

			if (log.isDebugEnabled()) {
				log.debug("Decoding record at offset {}.", buffer.position());
			}
			Map<String, Object> decodedRecord = recordEncoding.decode(buffer);
			if (log.isDebugEnabled()) {
				log.debug("Offset after decoding record: {}.", buffer.position());
			}

			collectRecord(recordEncoding.getEmbeddedSelector(), hits, decodedRecord);
		}

		result.add(hits);

		if (log.isDebugEnabled()) {
			log.debug("There were {}  good hits and {} false hit(s) for selector '{}'", goodHits, falseHits, selectorValue);
			log.debug("Returning: {}", result);
		}
		// logger.info("There were {} good hits and {} false hit(s) for selector '{}'", goodHits,
		// falseHits, selectorValue);
		log.info("There were {} Positive hits for selector '{}'", goodHits, selectorValue);

		return result;
	}

	/**
	 * @param hits
	 * @param decodedRecord
	 */
	private void collectRecord(int embeddedSelector, Hits hits, Map<String, Object> decodedRecord) {
		final QuerySchema qSchema = queryInfo.getQuerySchema();

		ClearTextQueryResponse.Record record = new ClearTextQueryResponse.Record();
		record.setSelector(embeddedSelector);

		// logger.info("Field Count to Extract {}", fieldsToExtract.size());
		for (QuerySchemaElement qsField : qSchema.getElementList()) {
			final String fieldName = qsField.getName();

			// We do not need to caputure the selector field in record (redundant)
			if (fieldName.equals(queryInfo.getQuerySchema().getSelectorField())) continue;

			record.add(fieldName, decodedRecord.get(fieldName));
		}

		if (shouldAddHit(record)) {
			hits.add(record);
			++goodHits;
		}
	}

	/**
	 * @param buffer
	 */
	private void skipGap(ByteBuffer buffer) {
		while (buffer.hasRemaining()) {
			buffer.mark();
			if (buffer.get() != 0x00) {
				buffer.reset();
				break;
			}
		}
	}

	private boolean shouldAddHit(Record record) {
		final String expectedSelectorValue = embedSelectorMap.get(selectorIndex);
		boolean result = Objects.equals(expectedSelectorValue, record.getSelector().toString());
		if (!result) {
			// logger.info("Embedded selector does not match original: '{}' != '{}'", selectorValue,
			// expectedSelectorValue);
			falseHits++;
		}
		return result;
	}
}
