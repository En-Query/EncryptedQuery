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

import java.io.IOException;
import java.io.PrintWriter;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.io.FileOutputFormat;
import org.apache.flink.api.java.tuple.Tuple2;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.xml.transformation.ResponseWriter;

@SuppressWarnings("serial")
public class ResponseFileOutputFormat extends FileOutputFormat<Tuple2<Integer, CipherText>> {

	private final QueryInfo queryInfo;
	private PrintWriter pw;

	public ResponseFileOutputFormat(QueryInfo queryInfo) {
		Validate.notNull(queryInfo);
		this.queryInfo = queryInfo;
	}


	@Override
	public void open(int taskNumber, int numTasks) throws IOException {
		super.open(taskNumber, numTasks);

		// OutputStream output = new FileOutputStream(outputFilePath.toFile(), true);
		try (ResponseWriter rw = new ResponseWriter(stream, false)) {
			rw.writeBeginDocument();
			rw.writeBeginResponse();
			rw.write(queryInfo);
		} catch (XMLStreamException e) {
			throw new IOException("Error writing header", e);
		}
		pw = new PrintWriter(stream);
		// try (PrintWriter pw = new PrintWriter(stream);) {
		pw.println("<resultSet xmlns=\"http://enquery.net/encryptedquery/response\">");
		// }
	}

	@Override
	public void writeRecord(Tuple2<Integer, CipherText> record) throws IOException {
		CipherText cipherText = record.f1;
		if (cipherText == null) return;
		Integer column = record.f0;

		// OutputStream output = new FileOutputStream(file.toFile(), true);
		// try (PrintWriter pw = new PrintWriter(stream);) {
		pw.print("<result column=\"");
		pw.print(column);
		pw.print("\" value=\"");
		pw.print(Base64.encodeBase64String(cipherText.toBytes()));
		pw.println("\"/>");

		// }
	}

	@Override
	public void close() throws IOException {
		// OutputStream output = new FileOutputStream(inProgressFile.toFile(), true);
		// try (PrintWriter pw = new PrintWriter(stream);) {
		pw.println("</resultSet>");
		pw.println("</resp:response>");
		pw.flush();
		super.close();
	}
}
