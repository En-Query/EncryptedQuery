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
package org.enquery.encryptedquery.hadoop.core;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.utils.FileIOUtils;
import org.enquery.encryptedquery.xml.transformation.QueryReader;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.enquery.encryptedquery.xml.transformation.ResponseTypeConverter;

/**
 * Loads Query, QueryInfo, configuration file, Crypto Scheme, etc. from Hadoop distributed cache.
 * This is local disk with symlinks where the file name is always query.xml in the current
 * directory.
 */
public class DistCacheLoader {

	private final CryptoScheme crypto;
	private final QueryTypeConverter queryConverter;
	private final ResponseTypeConverter responseConverter;
	private final Map<String, String> config;
	private QueryTypeConverter queryTypeConverter;

	/**
	 * @throws Exception
	 * @throws IOException
	 * @throws FileNotFoundException
	 * 
	 */
	public DistCacheLoader() throws FileNotFoundException, IOException, Exception {

		config = FileIOUtils.loadPropertyFile(Paths.get("config.properties"));
		crypto = CryptoSchemeFactory.make(config);
		final CryptoSchemeRegistry cryptoRegistry = new CryptoSchemeRegistry() {
			@Override
			public CryptoScheme cryptoSchemeByName(String schemeId) {
				if (schemeId.equals(crypto.name())) {
					return crypto;
				}
				return null;
			}
		};

		queryConverter = new QueryTypeConverter();
		queryConverter.setCryptoRegistry(cryptoRegistry);
		queryConverter.initialize();

		responseConverter = new ResponseTypeConverter();
		responseConverter.setQueryConverter(queryConverter);
		responseConverter.setSchemeRegistry(cryptoRegistry);
		responseConverter.initialize();

		queryTypeConverter = new QueryTypeConverter();
		queryTypeConverter.setCryptoRegistry(cryptoRegistry);
		queryTypeConverter.initialize();
	}

	public QueryInfo loadQueryInfo() throws Exception {
		try (InputStream inputStream = new FileInputStream("query.xml");
				QueryReader qr = new QueryReader();) {

			qr.parse(inputStream);
			return queryConverter.fromXMLQueryInfo(qr.getQueryInfo());
		}
	}

	public Query loadQuery() throws FileNotFoundException, IOException, JAXBException {
		try (InputStream inputStream = new FileInputStream("query.xml")) {
			return queryTypeConverter.toCoreQuery(queryTypeConverter.unmarshal(inputStream));
		}
	}

	public CryptoScheme getCrypto() {
		return crypto;
	}

	public ResponseTypeConverter getResponseConverter() {
		return responseConverter;
	}

	/**
	 * @throws Exception
	 * 
	 */
	public void close() throws Exception {
		if (crypto != null) {
			crypto.close();
		}
	}
}
