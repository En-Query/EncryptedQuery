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
package org.enquery.encryptedquery.flink.batch;

import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.configuration.Configuration;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.filter.RecordFilter;

/**
 *
 */
public class FlinkBatchRecordFilter extends RichFilterFunction<Map<String, Object>> {
	private static final long serialVersionUID = 1L;
	private final String filterExpr;
	private final String selectorFieldName;
	private final DataSchema dataSchema;
	private transient RecordFilter recordFilter;

	/**
	 * @param filterExpr
	 * @param sfn
	 */
	public FlinkBatchRecordFilter(Query query) {
		Validate.notNull(query);
		this.filterExpr = query.getQueryInfo().getFilterExpression();
		this.selectorFieldName = query.getQueryInfo().getQuerySchema().getSelectorField();
		this.dataSchema = query.getQueryInfo().getQuerySchema().getDataSchema();
	}

	@Override
	public void open(Configuration parameters) throws Exception {
		super.open(parameters);
		if (filterExpr != null) {
			this.recordFilter = new RecordFilter(filterExpr, dataSchema);
		}
	}



	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.flink.api.common.functions.FilterFunction#filter(java.lang.Object)
	 */
	@Override
	public boolean filter(Map<String, Object> row) throws Exception {
		if (row.get(selectorFieldName) == null) return false;
		if (recordFilter != null) return recordFilter.satisfiesFilter(row);
		return true;
	}
}
