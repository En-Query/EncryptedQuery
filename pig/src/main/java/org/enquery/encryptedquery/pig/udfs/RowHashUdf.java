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

import org.apache.commons.lang3.Validate;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.utils.KeyedHash;

public class RowHashUdf extends AbstractUdf<Integer> implements PigTypes {

	private int bitSize;
	private String key;
	private String selectorFieldName;

	public RowHashUdf(String queryFileName, String configFileName) {
		super(queryFileName, configFileName);
	}

	@Override
	public Integer exec(Tuple input) throws IOException {
		if (input == null || input.size() < 1) return null;

		initializeQueryParams();

		final boolean debugging = log.isDebugEnabled();
		final String aliasName = getPrefixedAliasName(null, selectorFieldName);
		Validate.notNull(aliasName, "Alias was not found for selector field: %s.", selectorFieldName);

		if (debugging) {
			log.debug(MessageFormat.format("Found alias ''{0}'' for selector field ''{1}''.", aliasName, selectorFieldName));
		}

		final String type = pigToPirDataType(input.getType(getPosition(aliasName)));
		final String value = asString(getObject(input, aliasName), type);
		if (value == null) return null;
		return KeyedHash.hash(key, bitSize, value);
	}

	/**
	 * Load the query from distributed cache and grab the parameters we need this occurs only once
	 * for each instance
	 * 
	 * @throws IOException
	 */
	private void initializeQueryParams() throws IOException {
		final boolean debugging = log.isDebugEnabled();

		initializeQuery(query -> {
			final QueryInfo queryInfo = query.getQueryInfo();
			selectorFieldName = queryInfo.getQuerySchema().getSelectorField();
			Validate.notBlank(selectorFieldName, "Selector field can't be blank.");
			bitSize = queryInfo.getHashBitSize();
			// TODO: key should be Hex decoded?
			key = queryInfo.getHashKey();
			if (debugging) {
				log.debug(MessageFormat.format("Loaded query params bitSize: {0}, key: {1}, selector: {2}",
						bitSize,
						key,
						selectorFieldName));
			}
		});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * datafu.pig.util.AliasableEvalFunc#getOutputSchema(org.apache.pig.impl.logicalLayer.schema.
	 * Schema)
	 * 
	 * Output schema is a 'rowHash' field of type int followed by all fields from the input schema
	 */
	@Override
	public Schema getOutputSchema(Schema input) {
		return null;
	}
}
