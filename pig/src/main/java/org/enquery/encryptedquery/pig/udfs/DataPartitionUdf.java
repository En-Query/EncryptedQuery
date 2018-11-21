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
package org.enquery.encryptedquery.pig.udfs;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.BagFactory;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.DataType;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.impl.logicalLayer.FrontendException;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.apache.pig.impl.logicalLayer.schema.Schema.FieldSchema;
import org.enquery.encryptedquery.core.Partitioner;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.enquery.encryptedquery.data.QuerySchemaElement;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.utils.KeyedHash;
import org.enquery.encryptedquery.utils.PIRException;

public class DataPartitionUdf extends AbstractUdf<DataBag> implements PigTypes {
	private final BagFactory bagFactory = BagFactory.getInstance();
	private final TupleFactory tupleFactory = TupleFactory.getInstance();
	private Partitioner partitioner;
	private String selectorFieldName;
	private boolean embedSelector;
	private DataSchema dSchema;

	public DataPartitionUdf(String queryFileName, String configFileName) {
		super(queryFileName, configFileName);
	}

	@Override
	public DataBag exec(Tuple input) throws IOException {
		if (input == null || input.size() == 0) return null;
		initializeQueryParams();
		return makeBagFromParts(collectParts(input));
	}

	private DataBag makeBagFromParts(final List<Byte> parts) throws ExecException {
		final DataBag bag = bagFactory.newDefaultBag();
		for (int col = 0; col < parts.size(); ++col) {
			final Tuple tuple = tupleFactory.newTuple(2);
			tuple.set(0, col);
			tuple.set(1, parts.get(col));
			bag.add(tuple);
		}
		return bag;
	}

	private void initializeQueryParams() throws IOException {
		initializeQuery(q -> {
			final QueryInfo queryInfo = q.getQueryInfo();
			partitioner = new Partitioner();
			selectorFieldName = queryInfo.getQuerySchema().getSelectorField();
			embedSelector = queryInfo.getEmbedSelector();
			dSchema = queryInfo.getQuerySchema().getDataSchema();
		});
	}

	private List<Byte> collectParts(Tuple input) throws ExecException, FrontendException {
		final List<Byte> parts = new ArrayList<>();

		if (input.size() > 1) {
			singleTuple(input, parts, null);
		} else {
			bagOfTuples(input, parts);
		}
		return parts;
	}

	private void singleTuple(Tuple input, List<Byte> parts, String prefix) throws ExecException {
		if (embedSelector) {
			collectSelectorParts(input, parts, prefix);
		}
		collectDataParts(input, parts, prefix);
	}

	private void bagOfTuples(Tuple input, List<Byte> parts) throws ExecException, FrontendException {
		// when input is a bag of tuples, there is only one field?
		DataBag outerBag = (DataBag) input.get(0);

		String prefix = getInputSchema().getField(0).alias;
		// log.info("Using prefix: " + prefix);
		for (Tuple tuple : outerBag) {
			singleTuple(tuple, parts, prefix);
		}
	}

	@Override
	public Schema getOutputSchema(Schema input) {
		final Schema tupleSchema = new Schema();
		tupleSchema.add(new FieldSchema("col", DataType.INTEGER));
		tupleSchema.add(new FieldSchema("part", DataType.BYTE));

		final String schemaName = getSchemaName(this.getClass().getName().toLowerCase(), input);
		Schema.FieldSchema fieldSchema;
		try {
			fieldSchema = new Schema.FieldSchema(schemaName, tupleSchema, DataType.BAG);
		} catch (FrontendException e) {
			throw new RuntimeException("Error constructing outpur schema.", e);
		}
		return new Schema(fieldSchema);
	}

	private void collectSelectorParts(Tuple input, List<Byte> parts, String prefix) throws ExecException {
		try {
			final String aliasName = getPrefixedAliasName(prefix, selectorFieldName);
			log.debug("Alias of selector is: " + aliasName);
			Validate.notNull(aliasName);

			Integer position = getPosition(aliasName);
			Validate.notNull(position, "Alias not found in input tuple.");

			final String type = pigToPirDataType(input.getType(position));
			final String value = asString(getObject(input, aliasName), type);

			if (log.isDebugEnabled()) {
				log.debug(
						MessageFormat.format(
								"Generating parts for selector field '{0}' of type '{1}' and value '{2}'",
								selectorFieldName, type, value));
			}
			int hashedSelector = KeyedHash.hash("aux", 32, value);
			parts.addAll(partitioner.fieldToBytes(hashedSelector, "int", null));
		} catch (PIRException e) {
			throw new ExecException("Error embedding selector.", e);
		}
	}

	private void collectDataParts(Tuple input, List<Byte> parts, String prefix) throws ExecException {
		final boolean debugging = log.isDebugEnabled();

		if (debugging) {
			log.debug(Arrays.toString(getFieldAliases().entrySet().toArray()));
		}

		for (final QuerySchemaElement element : querySchema.getElementList()) {
			final String aliasName = getPrefixedAliasName(prefix, element.getName());

			if (debugging) log.debug("Looking up name: '" + aliasName + "'");

			final Integer index = getPosition(aliasName);

			if (debugging) log.debug("Index of '" + aliasName + "' is " + index);
			final Object value = getObject(input, aliasName);

			if (debugging) log.debug("Value of '" + aliasName + "' is " + value);

			if (value == null) return;

			try {
				DataSchemaElement dse = dSchema.elementByName(element.getName());
				partitioner.collectParts(parts, value, dse.getDataType(), element);
			} catch (PIRException e) {
				throw new ExecException("Partitioner error.", e);
			}
		}
	}
}
