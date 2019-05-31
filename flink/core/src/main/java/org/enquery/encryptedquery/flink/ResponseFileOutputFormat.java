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
package org.enquery.encryptedquery.flink;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.io.FileOutputFormat;
import org.apache.flink.api.java.tuple.Tuple2;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.enquery.encryptedquery.xml.transformation.ResponseTypeConverter;

@SuppressWarnings("serial")
public class ResponseFileOutputFormat extends FileOutputFormat<Tuple2<Integer, CipherText>> {

	private final QueryInfo queryInfo;
	private final Map<Integer, CipherText> map = new TreeMap<>();
	private final Map<String, String> config;

	public ResponseFileOutputFormat(QueryInfo queryInfo, Map<String, String> config) {
		Validate.notNull(queryInfo);
		Validate.notNull(config);
		this.config = config;
		this.queryInfo = queryInfo;
	}

	@Override
	public void writeRecord(Tuple2<Integer, CipherText> record) throws IOException {
		map.put(record.f0, record.f1);
	}

	@Override
	public void close() throws IOException {
		try {
			try (final CryptoScheme crypto = CryptoSchemeFactory.make(config)) {

				final CryptoSchemeRegistry registry = new CryptoSchemeRegistry() {
					@Override
					public CryptoScheme cryptoSchemeByName(String schemeId) {
						if (schemeId == null) return null;
						if (schemeId.equals(crypto.name())) return crypto;
						return null;
					}
				};

				QueryTypeConverter queryConverter = new QueryTypeConverter();
				queryConverter.setCryptoRegistry(registry);
				queryConverter.initialize();

				ResponseTypeConverter converter = new ResponseTypeConverter();
				converter.setQueryConverter(queryConverter);
				converter.setSchemeRegistry(registry);
				converter.initialize();

				Response response = new Response(queryInfo);
				response.addResponseElements(map);
				converter.marshal(converter.toXML(response), stream);
			}

		} catch (Exception e) {
			throw new IOException("Error saving response.", e);
		}
	}
}
