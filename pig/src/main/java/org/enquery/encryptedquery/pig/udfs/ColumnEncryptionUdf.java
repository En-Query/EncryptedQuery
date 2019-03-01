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

import org.apache.commons.lang3.Validate;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.logicalLayer.FrontendException;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.responder.ResponderProperties;

public class ColumnEncryptionUdf extends AbstractUdf<CipherText> implements PigTypes, ResponderProperties {
	transient private ColumnProcessor cec;
	// transient private CryptoScheme scheme;


	public ColumnEncryptionUdf(String queryFileName, String configFileName) {
		super(queryFileName, configFileName);
	}

	@Override
	public CipherText exec(Tuple input) throws IOException {
		if (input == null || input.size() == 0) return null;
		initializeQueryParams();
		return encryptColumn(input);
	}

	private void initializeQueryParams() throws IOException {
		initializeQuery(q -> {
			// Map<Integer, CipherText> queryElements = q.getQueryElements();

			// TODO: initialize ColumnProcessor and Scheme

			// BigInteger nSquared = q.getNSquared();
			// try {
			// String cecClassName = systemConfig.get(COLUMN_ENCRYPTION_CLASS_NAME);
			// Validate.notEmpty(cecClassName, "Missing or invalid property " +
			// COLUMN_ENCRYPTION_CLASS_NAME);
			//
			// Class<ComputeEncryptedColumn> cecClass;
			// cecClass = (Class<ComputeEncryptedColumn>) Class.forName(cecClassName);
			// cec = cecClass.newInstance();
			//
			// String modPowClassName = systemConfig.get(MOD_POW_CLASS_NAME);
			// Validate.notEmpty(modPowClassName, "Missing or invalid property " +
			// MOD_POW_CLASS_NAME);
			// Class<ModPowAbstraction> modPowClass = (Class<ModPowAbstraction>)
			// Class.forName(modPowClassName);
			// ModPowAbstraction modPowAbstraction = modPowClass.newInstance();

			// cec.initialize(queryElements, nSquared, modPowAbstraction, systemConfig);
			// } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e)
			// {
			// throw new RuntimeException("Error initializing ComputeEncryptedColumn", e);
			// }
		});
	}


	private CipherText encryptColumn(Tuple input) throws ExecException, FrontendException {
		Validate.isTrue(input.size() == 1, "Wrong input, expects a single field of type BAG.");
		Validate.isTrue(org.apache.pig.data.DataType.BAG == input.getType(0), "Wrong input, expects a single field of type BAG.");

		for (Tuple tuple : (DataBag) input.get(0)) {
			addEntry(tuple);
		}

		return cec.compute();
	}

	private void addEntry(Tuple tuple) throws ExecException {
		Validate.isTrue(tuple.size() == 2, "Tuple within bag is expected to have two values <int, Byte>.");
		Validate.isTrue(org.apache.pig.data.DataType.INTEGER == tuple.getType(0));
		Validate.isTrue(org.apache.pig.data.DataType.BYTE == tuple.getType(1));

		final int rowHash = (int) tuple.get(0);
		byte[] ba = new byte[1];
		ba[0] = tuple.getType(1);
		// BigInteger bi = new BigInteger(1, ba);
		cec.insert(rowHash, ba);
	}

	@Override
	public Schema getOutputSchema(Schema input) {
		return null;
	}
}
