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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Record;
import org.enquery.encryptedquery.data.QueryInfo;
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
	private static final Logger logger = LoggerFactory.getLogger(DecryptResponseSelectorTask.class);

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

		int dataChunkSize = queryInfo.getDataChunkSize();


		logger.debug("Number of Columns = ( " + rElements.size() + " )");
		logger.info("Processing selector value = {}", selectorValue);

		final List<byte[]> partitions = new ArrayList<>();
		boolean zeroElement = collectChunks(partitions);
		if (zeroElement) {
			// logger.warn("Partitions are all zeroes.");
			return result;
		}

		processRecords(hits, dataChunkSize, partitions);

		result.add(hits);

		if (logger.isDebugEnabled()) {
			logger.debug("There were {}  good hits and {} false hit(s) for selector '{}'", goodHits, falseHits, selectorValue);
			logger.debug("Returning: {}", result);
		}
		logger.info("There were {}  good hits and {} false hit(s) for selector '{}'", goodHits, falseHits, selectorValue);

		return result;
	}

	private void processRecords(final ClearTextQueryResponse.Hits hits, int dataChunkSize, final List<byte[]> partitions) throws PIRException {
		final int bytesPerPartition = dataChunkSize; // calcBytesPerPartition(dataPartitionBitSize);
		final List<Byte> parts = makeParts(partitions, bytesPerPartition);
		final List<Record> returnRecords = RecordExtractor.getQueryResponseRecords(queryInfo, parts, bytesPerPartition);
		for (Record record : returnRecords) {
			if (shouldAddHit(record)) {
				hits.add(record);
				++goodHits;
			}
		}
	}

	private boolean shouldAddHit(Record record) {
		if (!queryInfo.getEmbedSelector()) return true;

		final String expectedSelectorValue = embedSelectorMap.get(selectorIndex);
		boolean result = Objects.equals(expectedSelectorValue, record.getSelector().toString());
		if (!result) {
			// logger.info("Embedded selector does not match original: '{}' != '{}'", selectorValue,
			// expectedSelectorValue);
			falseHits++;
		}
		return result;
	}

	private List<Byte> makeParts(List<byte[]> partitions, int bytesPerPartition) {
		List<Byte> result = new ArrayList<>();
		for (byte[] partitionBytes : partitions) {
			for (byte b : partitionBytes) {
				result.add(b);
			}
		}
		return result;
	}

	private boolean collectChunks(List<byte[]> chunks) {
		boolean result = true;
		for (int partNum = 0; partNum < rElements.size(); partNum++) {
			final PlainText plainText = rElements.get(partNum);
			final byte[] chunk = crypto.plainTextChunk(queryInfo, plainText, selectorIndex);
			chunks.add(chunk);
			result = result && isAllZeroes(chunk);
		}
		return result;
	}

	/**
	 * @param chunk
	 * @return
	 */
	private boolean isAllZeroes(byte[] chunk) {
		for (byte b : chunk) {
			if (b != 0) {
				return false;
			}
		}
		return true;
	}
}
