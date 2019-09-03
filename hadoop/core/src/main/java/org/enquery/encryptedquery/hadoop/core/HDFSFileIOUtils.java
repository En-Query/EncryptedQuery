package org.enquery.encryptedquery.hadoop.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HDFSFileIOUtils {

	private static final Logger log = LoggerFactory.getLogger(HDFSFileIOUtils.class);

	public static void copyHDFSFileToLocal(FileSystem hdfs, Path hdfsFile, java.nio.file.Path localFile) throws IOException {
		log.info("Writing HDFS file {} into Local Folder {}", hdfsFile.toString(), localFile.toAbsolutePath().toString());

		try (FSDataInputStream inputStream = hdfs.open(hdfsFile);
				OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(localFile.toAbsolutePath().toString()));) {
			IOUtils.copyBytes(inputStream, outputStream, hdfs.getConf());
		}
	}

	public static void copyLocalFileToHDFS(FileSystem hdfs, String hdfsFolder, java.nio.file.Path nativeFile) throws IOException {

		log.info("Writing Local File {} info HDFS Folder {}", nativeFile.toAbsolutePath(), hdfsFolder);

		org.apache.hadoop.fs.Path hdfsFile = new org.apache.hadoop.fs.Path(hdfsFolder + Path.SEPARATOR + nativeFile.getFileName().toString());

		try (FSDataOutputStream outputStream = hdfs.create(hdfsFile);
				InputStream inputStream = new BufferedInputStream(new FileInputStream(nativeFile.toAbsolutePath().toString()));) {
			IOUtils.copyBytes(inputStream, outputStream, hdfs.getConf());
		}
	}

}
