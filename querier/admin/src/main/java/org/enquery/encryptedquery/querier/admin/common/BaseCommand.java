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
package org.enquery.encryptedquery.querier.admin.common;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.TimeZone;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.console.Session;
import org.fusesource.jansi.Ansi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class BaseCommand {

	private static final Logger log = LoggerFactory.getLogger(BaseCommand.class);

	protected static final Ansi.Color ERROR_COLOR = Ansi.Color.RED;
	private static final Ansi.Color HEADER_COLOR = Ansi.Color.CYAN;
	private static final Ansi.Color SUCCESS_COLOR = Ansi.Color.GREEN;

	protected static DateTimeFormatter screenDateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
	protected static TimeZone utc = TimeZone.getTimeZone("UTC");
	protected static DateTimeFormatter filenameDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'hhmmss");

	protected final PrintStream out = System.out;
	@Reference
	protected Session session;


	public Session getSession() {
		return session;
	}

	public void setSession(Session session) {
		this.session = session;
	}

	protected void printColor(Ansi.Color color, String message) {
		log.info(message);
		String colorString;
		if (color == null || color.equals(Ansi.Color.DEFAULT)) {
			colorString = Ansi.ansi().reset().toString();
		} else {
			colorString = Ansi.ansi().fg(color).toString();
		}
		out.print(colorString);
		out.print(message);
		out.println(Ansi.ansi().reset().toString());
	}

	protected void printError(String message) {
		printColor(ERROR_COLOR, message);
	}

	protected void printHeader(String message) {
		printColor(HEADER_COLOR, message);
	}

	protected void printSuccess(String message) {
		printColor(SUCCESS_COLOR, message);
	}

	protected Long enterPositiveLong(String prompt) throws IOException {
		return enterPositiveLong(prompt, i -> i > 0);
	}

	/**
	 * @param enterHadoopNumCpuCoresPerNode
	 * @throws IOException
	 */
	protected Integer enterPositiveInt(String prompt) throws IOException {
		return enterPositiveInt(prompt, i -> i > 0);

	}

	protected Boolean inputBoolean(String prompt) throws IOException {
		String input = readLine(prompt);
		return input.matches("^[yY][eE]?[sS]?$");
	}


	protected Integer enterPositiveInt(String prompt, Predicate<Integer> validator) throws IOException {
		int tries = 3;
		Integer result = null;
		while (tries-- > 0 && result == null) {
			String value = readLine(prompt);
			Integer v = null;
			try {
				v = Integer.parseUnsignedInt(value);
			} catch (NumberFormatException e) {
				out.println("Invalid input.");
			}

			if (validator.test(v)) {
				result = v;
			} else {
				out.println("Value is outside valid range.");
			}
		}
		return result;
	}

	protected Long enterPositiveLong(String prompt, Predicate<Long> validator) throws IOException {
		int tries = 3;
		Long result = null;
		while (tries-- > 0 && result == null) {
			String valueStr = readLine(prompt);
			long valueLong = 0;
			try {
				valueLong = Long.parseUnsignedLong(valueStr);
			} catch (NumberFormatException e) {
				out.println("Invalid input.");
			}
			if (validator.test(valueLong)) {
				result = valueLong;
			} else {
				out.println("Value is outside valid range.");
			}
		}
		return result;
	}

	protected Path enterExistingFile(String prompt) throws IOException {
		int tries = 3;
		Path result = null;
		while (result == null && tries-- > 0) {
			String line = readLine(prompt);
			if (StringUtils.isBlank(line)) {
				out.println("Enter a valid file name.");
				continue;
			}
			Path f = Paths.get(line);
			if (!Files.exists(f)) {
				out.println("File does not exists.");
			}
			if (!Files.isRegularFile(f)) {
				out.println("Not a file.");
			}
			result = f;
		}
		return result;
	}

	protected Path enterExistingDir(String prompt) throws IOException {
		int tries = 3;
		Path result = null;
		while (result == null && tries-- > 0) {
			String line = readLine(prompt);
			if (StringUtils.isBlank(line)) {
				out.println("Enter a valid directory name.");
				continue;
			}
			Path f = Paths.get(line);
			if (!Files.exists(f)) {
				out.println("Directory does not exists.");
			} else if (!Files.isDirectory(f)) {
				out.println("Not a directory.");
			} else {
				result = f;
			}
		}
		return result;
	}

	protected String readLine(String prompt) throws IOException {
		String result = session.readLine(prompt, null);
		if (result != null) result = result.trim();
		return result;
	}

	protected String toString(Date d) {
		if (d == null) return "";
		OffsetDateTime utcTime = d.toInstant().atZone(utc.toZoneId()).toOffsetDateTime();
		ZonedDateTime localTime = utcTime.atZoneSameInstant(ZoneId.systemDefault());
		return localTime.format(screenDateFormat);
	}

	/**
	 * @param file
	 * @return
	 */
	protected Path makeOutputFile(String baseName, Path dir) {
		String name = String.format("%s-%s.xml", baseName, LocalDateTime.now().format(filenameDateFormat));
		return dir.resolve(name);
	}

}
