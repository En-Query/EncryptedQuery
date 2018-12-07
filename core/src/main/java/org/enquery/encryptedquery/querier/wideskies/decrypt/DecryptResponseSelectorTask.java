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
package org.enquery.encryptedquery.querier.wideskies.decrypt;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Record;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.utils.ConversionUtils;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decrypt a single selector
 *
 */
class DecryptResponseSelectorTask implements Callable<ClearTextQueryResponse.Selector> {
	private static final Logger logger = LoggerFactory.getLogger(DecryptResponseSelectorTask.class);

	private final List<BigInteger> rElements;
	private final Map<String, BigInteger> selectorMaskMap;
	private final QueryInfo queryInfo;
	private final Map<Integer, String> embedSelectorMap;
	private final String selectorValue;
	private final int selectorIndex;

	private int falseHits = 0;
	private int goodHits = 0;

	public DecryptResponseSelectorTask(List<BigInteger> rElements,
			String selectorValue,
			int selectorIndex,
			Map<String, BigInteger> selectorMaskMap,
			QueryInfo queryInfo,
			Map<Integer, String> embedSelectorMap) {

		Validate.notNull(rElements, "rElements is null");
		Validate.notNull(selectorValue, "selectorValue is null");
		Validate.notNull(selectorMaskMap, "selectorMaskMap is null");
		Validate.notNull(queryInfo, "queryInfo is null");
		Validate.notNull(embedSelectorMap, "embedSelectorMap is null");

		this.rElements = rElements;
		this.selectorValue = selectorValue;
		this.selectorMaskMap = selectorMaskMap;
		this.queryInfo = queryInfo;
		this.embedSelectorMap = embedSelectorMap;
		this.selectorIndex = selectorIndex;

	}

	@Override
	public ClearTextQueryResponse.Selector call() throws PIRException {

		final ClearTextQueryResponse.Selector result = new ClearTextQueryResponse.Selector(
				queryInfo.getQuerySchema().getSelectorField());

		ClearTextQueryResponse.Hits hits = new ClearTextQueryResponse.Hits(selectorValue);

		int dataChunkSize = queryInfo.getDataChunkSize();


		logger.debug("Number of Columns = ( " + rElements.size() + " )");
		logger.info("Processing selector value = {}", selectorValue);

		final List<BigInteger> partitions = new ArrayList<>();
		boolean zeroElement = makePartitions(dataChunkSize, partitions);
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

	private void processRecords(final ClearTextQueryResponse.Hits hits, int dataChunkSize, final List<BigInteger> partitions) throws PIRException {
		final int bytesPerPartition = dataChunkSize;  //calcBytesPerPartition(dataPartitionBitSize);
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

	private int calcBytesPerPartition(int dataPartitionBitSize) throws PIRException {
		int result = 1;
		if ((dataPartitionBitSize % 8) == 0) {
			result = dataPartitionBitSize / 8;
		} else {
			throw new PIRException("dataPartitionBitSize must be a multiple of 8. Actual Value: " + dataPartitionBitSize);
		}

		if (logger.isDebugEnabled()) logger.debug("bytesPerPartition: {}", result);
		return result;
	}

	private List<Byte> makeParts(List<BigInteger> partitions, int bytesPerPartition) {
		List<Byte> result = new ArrayList<>();
		if (bytesPerPartition > 1) {
			for (BigInteger bi : partitions) {
				byte[] partitionBytes = ConversionUtils.bigIntegerToByteArray(bi, bytesPerPartition);
				for (byte b : partitionBytes) {
					result.add(b);
				}
			}
		} else {
			for (BigInteger bi : partitions) {
				result.add(bi.byteValue());
			}
		}

		return result;
	}

	private boolean makePartitions(int dataChunkSize, List<BigInteger> partitions) {
		boolean result = true;
		for (int partNum = 0; partNum < rElements.size(); partNum++) {
			// pull off the correct bits
			BigInteger part = (rElements.get(partNum)).and(selectorMaskMap.get(selectorValue));

			// logger.debug("rElements.get(" + (partNum) + ") = "
			// + rElements.get(partNum).toString(2) + " bitLength = "
			// + rElements.get(partNum).bitLength() + " val = "
			// + rElements.get(partNum));
			// logger.info("colNum ( {} ) part ( {} )", partNum,
			// Hex.encodeHexString(part.toByteArray()));

			part = part.shiftRight(selectorIndex * dataChunkSize*8);
			partitions.add(part);

			result = result && part.equals(BigInteger.ZERO);
		}
		return result;
	}
}
