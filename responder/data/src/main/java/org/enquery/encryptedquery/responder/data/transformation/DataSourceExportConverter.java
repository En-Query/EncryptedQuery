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
package org.enquery.encryptedquery.responder.data.transformation;

import java.util.Collection;

import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.xml.schema.DataSourceExport;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = DataSourceExportConverter.class)
public class DataSourceExportConverter extends org.enquery.encryptedquery.xml.transformation.DataSourceExportConverter {

	@Reference
	private DataSchemaTypeConverter dschemaConverter;
	@Reference
	private DataSourceTypeConverter dsrcConverter;

	public org.enquery.encryptedquery.xml.schema.DataSourceExport toXML(//
			Collection<DataSchema> dataSchemas,
			Collection<DataSource> dataSources) //
	{

		DataSourceExport result = new DataSourceExport();

		result.setDataSources(dsrcConverter.toXMLDataSources(dataSources));
		result.setDataSchemas(dschemaConverter.toXMLDataSchemas(dataSchemas));

		return result;
	}

}
