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
package org.enquery.encryptedquery.pig.storage;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.Validate;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.pig.data.Tuple;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.xml.transformation.ResponseTypeConverter;

/**
 *
 */
public class XMLRecordWriter extends
		RecordWriter<NullWritable, Tuple> {

	/**
	 * the outputstream to write out on
	 */
	private DataOutputStream out;
	private Map<Integer, CipherText> treeMap = new TreeMap<>();
	private QueryInfo queryInfo;

	/**
	 * 
	 */
	public XMLRecordWriter(DataOutputStream out) {
		this.out = out;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.hadoop.mapreduce.RecordWriter#close(org.apache.hadoop.mapreduce.
	 * TaskAttemptContext)
	 */
	@Override
	public void close(TaskAttemptContext arg0) throws IOException, InterruptedException {
		Validate.notNull(queryInfo);
		Response response = new Response(queryInfo);

		ResponseTypeConverter converter = new ResponseTypeConverter();
		response.addResponseElements(treeMap);
		org.enquery.encryptedquery.xml.schema.Response xml = converter.toXML(response);
		try {
			converter.marshal(xml, out);
		} catch (JAXBException e) {
			throw new IOException("Error saving  response.", e);
		}

		out.close();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.hadoop.mapreduce.RecordWriter#write(java.lang.Object, java.lang.Object)
	 */
	@Override
	public void write(NullWritable key, Tuple t) throws IOException,
			InterruptedException {
		if (treeMap == null) {
			treeMap = new TreeMap<>();
		}

		treeMap.put((Integer) t.get(0), (CipherText) t.get(1));
	}

	public QueryInfo getQueryInfo() {
		return queryInfo;
	}

	public void setQueryInfo(QueryInfo queryInfo) {
		this.queryInfo = queryInfo;
	}

}
