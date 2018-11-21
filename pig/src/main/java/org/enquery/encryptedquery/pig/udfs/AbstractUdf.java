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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

import javax.xml.bind.JAXBException;

import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.utils.FileIOUtils;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;

import datafu.pig.util.AliasableEvalFunc;

public abstract class AbstractUdf<T> extends AliasableEvalFunc<T> {

	// private static final String PIR_DATA_SCHEMA = "PIR_DATA_SCHEMA";
	private static final String PIR_QUERY = "PIR_QUERY";
	private static final String PIR_SYSTEM_CONFIG = "PIR_SYSTEM_CONFIG";

	private final Path queryFileName;
	// private final Path dataSchemaFileName;
	private final Path configFileName;

	private boolean initialized;
	protected QuerySchema querySchema;
	protected DataSchema dataSchema;
	protected Map<String, String> systemConfig;
	private QueryTypeConverter queryTypeConverter = new QueryTypeConverter();

	public AbstractUdf(String queryFileName, String configFileName) {
		super();
		this.queryFileName = Paths.get(queryFileName);
		// this.dataSchemaFileName = Paths.get(dataSchemaFileName);
		this.configFileName = Paths.get(configFileName);
	}

	@Override
	public Schema outputSchema(Schema input) {
		// we load the query and the data schema
		// and pass it to the back-end tasks
		// in the job configuration
		pushQueryAndDataSchemaToBackendWorkers();
		return super.outputSchema(input);
	}

	private void pushQueryAndDataSchemaToBackendWorkers() {
		final Properties properties = getInstanceProperties();
		// only once
		if (properties.get(PIR_QUERY) == null) {
			try {
				properties.put(PIR_QUERY, loadQuery(queryFileName));
				properties.put(PIR_SYSTEM_CONFIG, FileIOUtils.loadPropertyFile(configFileName));
			} catch (IOException | JAXBException e) {
				throw new RuntimeException("Error loading query and/or data schema.", e);
			}
		}
	}

	protected Query loadQuery(Path file) throws IOException, FileNotFoundException, JAXBException {
		log.info("Loading query from file: " + file);
		try (FileInputStream fis = new FileInputStream(file.toFile())) {
			org.enquery.encryptedquery.xml.schema.Query xml = queryTypeConverter.unmarshal(fis);
			return queryTypeConverter.toCoreQuery(xml);
		}
	}

	// private Query loadQuery(Path file) throws FileNotFoundException, IOException {
	// try (FileInputStream fis = new FileInputStream(file.toFile());
	// ObjectInputStream oin = new ObjectInputStream(fis)) {
	// return (Query) oin.readObject();
	// } catch (ClassNotFoundException e) {
	// throw new IOException("Error loading query from " + file);
	// }
	// }

	@SuppressWarnings("unchecked")
	protected void initializeQuery(Consumer<Query> paramLoader) throws IOException {
		if (initialized) return;

		final Properties instanceProperties = getInstanceProperties();
		Query query = (Query) instanceProperties.get(PIR_QUERY);
		if (query == null) throw new IOException("PIR_QUERY is null.");

		querySchema = query.getQueryInfo().getQuerySchema();
		if (querySchema == null) throw new IOException("query data schema is null.");

		dataSchema = query.getQueryInfo().getQuerySchema().getDataSchema();
		// (DataSchema) instanceProperties.get(PIR_DATA_SCHEMA);
		// if (dataSchema == null) throw new IOException("PIR_DATA_SCHEMA is null.");

		systemConfig = (Map<String, String>) instanceProperties.get(PIR_SYSTEM_CONFIG);

		// hook for sub classes to load additional items
		if (paramLoader != null) paramLoader.accept(query);
	}

}
