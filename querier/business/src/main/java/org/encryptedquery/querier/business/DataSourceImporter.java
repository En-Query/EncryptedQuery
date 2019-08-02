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
package org.encryptedquery.querier.business;

import java.io.InputStream;

import javax.xml.bind.JAXBException;

import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.data.service.DataSchemaRepository;
import org.enquery.encryptedquery.querier.data.service.DataSourceRepository;
import org.enquery.encryptedquery.querier.data.transformation.DataSchemaTypeConverter;
import org.enquery.encryptedquery.querier.data.transformation.DataSourceTypeConverter;
import org.enquery.encryptedquery.xml.schema.DataSchemaResource;
import org.enquery.encryptedquery.xml.schema.DataSourceExport;
import org.enquery.encryptedquery.xml.schema.DataSourceResource;
import org.enquery.encryptedquery.xml.transformation.DataSourceExportConverter;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = DataSourceImporter.class)
public class DataSourceImporter {

	@Reference
	private DataSchemaRepository dataSchemaRepo;
	@Reference
	private DataSourceRepository dataSourceRepo;
	@Reference
	private DataSourceExportConverter exportConverter;
	@Reference
	private DataSchemaTypeConverter dataSchemaConverter;
	@Reference
	private DataSourceTypeConverter dataSourceConverter;

	public void importDataSources(InputStream inputStream) throws JAXBException {

		DataSourceExport exportedData = exportConverter.unmarshal(inputStream);
		for (DataSchemaResource dataSchema : exportedData.getDataSchemas().getDataSchemaResource()) {
			DataSchema ds = dataSchemaConverter.toJPA(dataSchema.getDataSchema());
			dataSchemaRepo.addOrUpdate(ds);
		}

		for (DataSourceResource resource : exportedData.getDataSources().getDataSourceResource()) {
			DataSource jpa = dataSourceConverter.toJPA(resource);
			dataSourceRepo.addOrUpdate(jpa);
		}
	}
}
