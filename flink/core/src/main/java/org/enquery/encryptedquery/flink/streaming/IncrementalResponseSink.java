package org.enquery.encryptedquery.flink.streaming;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.xml.transformation.ResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Incrementally saves the query result for a time window, one
 * <code>CipherTextAndColumnNumber</code> at a time. It starts writing to a temporary "columns" file
 * until it receives an EOF signal, then it creates the final response file. <br/>
 * <br/>
 * It does not hold all the response in memory at any point.
 * 
 * @see {@link ResponseFileNameBuilder#makeFinalFileName}.
 * @see {@link ResponseFileNameBuilder#makeInProgressFileName}
 * @see {@link CipherTextAndColumnNumber#isEof}
 */
public class IncrementalResponseSink extends RichSinkFunction<CipherTextAndColumnNumber> {

	private static final Logger log = LoggerFactory.getLogger(IncrementalResponseSink.class);
	private static final long serialVersionUID = -1514106781425205801L;

	private final QueryInfo queryInfo;
	private final String basePath;

	private transient Path rootPath;

	// keep track of missing columns by file name
	private Map<String, BitSet> columnsSeenPerFile = new HashMap<>();
	private Map<String, CipherTextAndColumnNumber> eofSignalsPerFile = new HashMap<>();

	public IncrementalResponseSink(QueryInfo queryInfo,
			Map<String, String> config,
			String basePath) {
		this.queryInfo = queryInfo;
		this.basePath = basePath;
	}

	@Override
	public void open(Configuration parameters) throws Exception {
		super.open(parameters);

		rootPath = Paths.get(basePath);
		// Files.createDirectories(rootPath);
		log.info("Initialized sink for path: {}", rootPath);
	}

	@Override
	public void close() throws Exception {
		super.close();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void invoke(CipherTextAndColumnNumber data, Context context) throws Exception {
		final long start = data.windowMinTimestamp;
		final long end = data.windowMaxTimestamp;
		// log.info("Received element {}", data);

		final Path file = ResponseFileNameBuilder.makeInProgressFileName(rootPath, start, end);
		Validate.isTrue(Files.exists(file), "File '%s' does not exists while processing %s", file, data.toString());

		if (data.isEof) {
			log.info("Received eof element of window {}", WindowInfo.windowInfo(start, end));
			final String fileName = file.toString();

			CipherTextAndColumnNumber prev = eofSignalsPerFile.get(fileName);
			if (prev == null) {
				eofSignalsPerFile.put(fileName, data);
			} else {
				prev.col = Math.max(prev.col, data.col);
			}
		} else {
			append(file, data);
		}

		if (receivedAllColumns(file)) {
			closeFile(file, data);
		}
	}

	/**
	 * True if we have received all column numbers (from 0 to eof.col - 1) for a given in progress
	 * file name.
	 * 
	 * @param file
	 * @return
	 */
	private boolean receivedAllColumns(Path file) {
		final boolean debugging = log.isDebugEnabled();
		final String fileName = file.toString();

		final BitSet bitset = columnsSeenPerFile.get(fileName);
		if (bitset == null) return false;

		CipherTextAndColumnNumber eofSignal = eofSignalsPerFile.get(fileName);
		if (eofSignal == null) return false;

		final int numberOfColumns = (int) eofSignal.col;
		for (int i = 0; i < numberOfColumns; ++i) {
			if (!bitset.get(i)) {
				if (debugging) log.debug("File '{}' is missing column {}.", file, i);
				return false;
			}
		}

		return true;
	}

	/**
	 * @param columnsFile
	 * @param data
	 * @throws IOException
	 * @throws XMLStreamException
	 * @throws DecoderException
	 */
	private void closeFile(Path inProgressFile, CipherTextAndColumnNumber data) throws IOException, XMLStreamException, DecoderException {
		log.info("Finishing file: {}", inProgressFile);

		final long end = data.windowMaxTimestamp;
		final long start = data.windowMinTimestamp;

		final Path finalFile = ResponseFileNameBuilder.makeFinalFileName(rootPath, start, end);
		// final Path windowCompletefile =
		// ResponseFileNameBuilder.makeWindowCompleteFileName(rootPath, start, end);

		try (OutputStream output = new FileOutputStream(inProgressFile.toFile(), true);
				PrintWriter pw = new PrintWriter(output);) {

			pw.println("</resultSet>");
			pw.println("</resp:response>");
		}

		// remove in memory data associated to this file
		final String fileName = inProgressFile.toString();
		eofSignalsPerFile.remove(fileName);
		columnsSeenPerFile.remove(fileName);

		// rename and delete tmp files
		FileUtils.moveFile(inProgressFile.toFile(), finalFile.toFile());
		log.info("Successfully created response file: {}", finalFile);
		// Files.deleteIfExists(windowCompletefile);
	}

	/**
	 * @param file
	 * @param window
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws XMLStreamException
	 */
	private void append(Path file, CipherTextAndColumnNumber data) throws FileNotFoundException, IOException, XMLStreamException {
		final boolean newFile = !anyColumsReceived(file);

		if (newFile) {
			try (OutputStream output = new FileOutputStream(file.toFile(), true);
					ResponseWriter rw = new ResponseWriter(output);) {
				rw.writeBeginDocument();
				rw.writeBeginResponse();
				rw.write(queryInfo);
			}
		}

		if (data.cipherText == null) return;

		try (OutputStream output = new FileOutputStream(file.toFile(), true);
				PrintWriter pw = new PrintWriter(output);) {
			if (newFile) {
				pw.println("<resultSet xmlns=\"http://enquery.net/encryptedquery/response\">");
			}
			pw.print("<result column=\"");
			pw.print((int) data.col);
			pw.print("\" value=\"");
			pw.print(Base64.encodeBase64String(data.cipherText.toBytes()));
			pw.println("\"/>");
		}
		columnAdded(file, (int) data.col);
	}

	/**
	 * @param file
	 * @return
	 */
	private boolean anyColumsReceived(Path file) {
		return columnsSeenPerFile.get(file.toString()) != null;
	}

	/**
	 * @param file
	 * @param col
	 * 
	 */
	private void columnAdded(Path file, int col) {
		final String fileName = file.toString();
		BitSet bitset = columnsSeenPerFile.get(fileName);
		if (bitset == null) {
			bitset = new BitSet(col);
			columnsSeenPerFile.put(fileName, bitset);
		}
		bitset.set(col);
	}

};
