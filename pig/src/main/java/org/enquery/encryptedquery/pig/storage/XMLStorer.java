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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang.Validate;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.pig.ResourceSchema;
import org.apache.pig.StoreFunc;
import org.apache.pig.StoreFuncInterface;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.util.UDFContext;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XMLStorer extends StoreFunc implements StoreFuncInterface {

	private static final Logger log = LoggerFactory.getLogger(XMLStorer.class);

	private static final String QUERY_INFO_PROPERTY_KEY = "query.info.key";
	private String queryFileName;
	private XMLRecordWriter recWriter;
	private String contextSignature = null;

	public XMLStorer(String queryFileName) throws IOException {
		this.queryFileName = queryFileName;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public OutputFormat getOutputFormat() throws IOException {
		return new XMLOutputFormat();
	}

	@Override
	public void setStoreLocation(String location, Job job) throws IOException {
		FileOutputFormat.setOutputPath(job, new Path(location));
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void prepareToWrite(RecordWriter writer) throws IOException {
		this.recWriter = (XMLRecordWriter) writer;
		Properties p = getUDFProperties();
		QueryInfo queryInfo = (QueryInfo) p.get(QUERY_INFO_PROPERTY_KEY);
		Validate.notNull(queryInfo, "Query info not found in the UDF context.");
		recWriter.setQueryInfo(queryInfo);
	}

	@Override
	public void putNext(Tuple t) throws IOException {
		try {
			recWriter.write(null, t);
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void checkSchema(ResourceSchema s) throws IOException {
		if (s.getFields().length != 2) {
			throw new IOException("Expects two fields.");
		}

		if (s.getFields()[0].getType() != org.apache.pig.data.DataType.INTEGER) {
			throw new IOException("Field 0 is expected to be integer.");
		}

		if (s.getFields()[1].getType() != org.apache.pig.data.DataType.BIGINTEGER) {
			throw new IOException("Field 0 is expected to be BigInteger.");
		}

		super.checkSchema(s);

		Properties p = getUDFProperties();
		p.put(QUERY_INFO_PROPERTY_KEY, loadQueryInfo());
	}

	private Properties getUDFProperties() {
		return UDFContext.getUDFContext()
				.getUDFProperties(this.getClass(), new String[] {contextSignature});
	}

	@Override
	public void setStoreFuncUDFContextSignature(String signature) {
		this.contextSignature = signature;
	}

	private QueryInfo loadQueryInfo() throws FileNotFoundException, IOException {
		log.info("Loading query from file: " + queryFileName);
		QueryTypeConverter queryTypeConverter = new QueryTypeConverter();

		try (FileInputStream fis = new FileInputStream(queryFileName)) {
			org.enquery.encryptedquery.xml.schema.Query xml = queryTypeConverter.unmarshal(fis);
			return queryTypeConverter.toCoreQuery(xml).getQueryInfo();
		} catch (JAXBException e) {
			e.printStackTrace();
			throw new IOException("Error loading Query", e);
		}
	}

}
