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
import java.math.BigInteger;
import java.util.TreeMap;

import javax.xml.bind.JAXBException;

import org.apache.flink.api.common.io.FileOutputFormat;
import org.apache.flink.api.java.tuple.Tuple2;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.xml.transformation.ResponseTypeConverter;

@SuppressWarnings("serial")
public class ResponseFileOutputFormat extends FileOutputFormat<Tuple2<Integer, BigInteger>> {

	private Response response;
	private TreeMap<Integer, BigInteger> treeMap = new TreeMap<>();

	public ResponseFileOutputFormat(QueryInfo queryInfo) {
		response = new Response(queryInfo);
	}

	@Override
	public void writeRecord(Tuple2<Integer, BigInteger> record) throws IOException {
		treeMap.put(record.f0, record.f1);
	}

	@Override
	public void close() throws IOException {
		ResponseTypeConverter converter = new ResponseTypeConverter();
		response.addResponseElements(treeMap);
		org.enquery.encryptedquery.xml.schema.Response xml = converter.toXML(response);
		try {
			converter.marshal(xml, stream);
		} catch (JAXBException e) {
			throw new IOException("Error saving  response.", e);
		}
		super.close();
	}


}
