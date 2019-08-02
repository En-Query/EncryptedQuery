package org.enquery.encryptedquery.hadoop.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HDFSFileIOUtils {

	private static final Logger log = LoggerFactory.getLogger(HDFSFileIOUtils.class);

	public static Map<String, String> loadPropertyFile(FileSystem hdfs, Path file) throws FileNotFoundException, IOException {
		Properties properties = new Properties();

		try (FSDataInputStream fis = new FSDataInputStream(hdfs.open(file))) {
			properties.load(fis);
		}
		HashMap<String, String> result = new HashMap<>();
		properties
				.entrySet()
				.stream()
				.forEach(
						entry -> result.put((String) entry.getKey(), (String) entry.getValue()));
		return result;
	}
	
	public static Map<String, String> loadConfig(FileSystem hdfs, Path file) {
		Map<String, String> config = new HashMap<String, String>();
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(hdfs.open(file)));
			String inputLine;
			inputLine = br.readLine();
			while (inputLine != null) {
				if (!inputLine.trim().startsWith("#") && inputLine.trim().length() > 0) {
					String[] line = inputLine.split("=");
					if (line[0] != null && line[1] != null) {
						config.put(line[0], line[1]);
					}
				}
				inputLine = br.readLine();
			}
            br.close();
			return config;

		} catch (Exception e) {
			log.error("Exception processing config file from hadoop: {}", e.getMessage());
		
			return null;
		}
	}
	
	public static String writeObjectToHDFS(FileSystem hdfs, Object objToWrite, String hdfsFileName) throws IOException {
		log.info("Writing {} into HDFS", hdfsFileName );

		org.apache.hadoop.fs.Path hdfswritepath = new org.apache.hadoop.fs.Path(hdfsFileName);
		
		if (hdfs.exists(hdfswritepath)) {
			hdfs.delete(hdfswritepath, false);
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutput out = null;
		byte[] outputBytes = null;
		try {
			out = new ObjectOutputStream(bos);
			out.writeObject(objToWrite);
			out.flush();
			outputBytes = bos.toByteArray();
		} finally {
			try {
			bos.close();
			} catch (IOException ex) {
				log.error("Failed to convert object {} to byte array", objToWrite.getClass().getName());
			}
		}

		FSDataOutputStream outputStream = hdfs.create(hdfswritepath);
		outputStream.write(outputBytes);
        outputStream.close();		
        
        return hdfsFileName;
	}
	
	public static void copyHDFSFileToLocal(FileSystem hdfs, Path hdfsFile, java.nio.file.Path localFile) throws IOException {

		log.info("Writing HDFS file {} info Local Folder {}", hdfsFile.toString(), localFile.toAbsolutePath().toString());
		
		if (hdfs.exists(hdfsFile)) {
			FSDataInputStream inputStream = hdfs.open(hdfsFile);

			OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(localFile.toAbsolutePath().toString()));
			IOUtils.copyBytes(inputStream,  outputStream, hdfs.getConf());
	        inputStream.close();		
			outputStream.close();
		} else {
			log.error("HDFS file does not exist.");
		}
		
	}
	
	public static void copyLocalFileToHDFS(FileSystem hdfs, String hdfsFolder, java.nio.file.Path nativeFile) throws IOException {

		log.info("Writing Local File {} info HDFS Folder {}", nativeFile.toAbsolutePath(), hdfsFolder);

		org.apache.hadoop.fs.Path hdfsFile = new org.apache.hadoop.fs.Path(hdfsFolder + Path.SEPARATOR + nativeFile.getFileName().toString());
		
		if (hdfs.exists(hdfsFile)) {
			hdfs.delete(hdfsFile, false);
		}

		FSDataOutputStream outputStream = hdfs.create(hdfsFile);

		InputStream inputStream = new BufferedInputStream(new FileInputStream(nativeFile.toAbsolutePath().toString()));
		IOUtils.copyBytes(inputStream,  outputStream, hdfs.getConf());
        inputStream.close();		
		outputStream.close();
		
	}

}
