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
package org.enquery.encryptedquery.filter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 *
 */
public class RecordFilterTest {

	@Test
	public void basicTest() {
		RecordFilter rf = new RecordFilter("age > 24");
		Map<String, Object> record = new HashMap<>();
		record.put("age", 20);

		assertFalse(rf.satisfiesFilter(record));

		record.put("age", 25);
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void andOrTest() {
		RecordFilter rf = new RecordFilter("price > 24 And \"count\" < 100");
		Map<String, Object> record = new HashMap<>();
		record.put("price", 20);
		record.put("count", 100);

		assertFalse(rf.satisfiesFilter(record));

		record.put("price", 30);
		record.put("count", 99);
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("\"count\" = 99");
		assertTrue(rf.satisfiesFilter(record));

		record.put("count", -99.87);
		rf = new RecordFilter("\"count\" = -99.87");
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void quotedFieldNames() {
		RecordFilter rf = new RecordFilter("\"last name\" = 'Smith'");
		Map<String, Object> record = new HashMap<>();
		record.put("last name", "Smith");

		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("\"last name\" <> 'Smith'");
		assertFalse(rf.satisfiesFilter(record));
	}

	@Test
	public void time() {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, -1);

		RecordFilter rf = new RecordFilter("CURRENT_TIMESTAMP > dob");
		Map<String, Object> record = new HashMap<>();
		record.put("dob", cal.getTime().toInstant());

		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("CURRENT_TIMESTAMP > TIMESTAMP '2001-07-04T14:23Z'");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("dob > TIMESTAMP '2001-07-04T14:23Z'");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("dob > DATE '2001-07-04'");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("TIMESTAMP '2001-07-04T00:00:00Z' = DATE '2001-07-04'");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("CURRENT_DATE = DATE '" + LocalDate.now().toString() + "'");
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void in() {
		Map<String, Object> record = new HashMap<>();
		RecordFilter rf = new RecordFilter("'sarah' IN children");
		record.put("children", Arrays.asList("joseph", "sarah"));

		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("3 in (1, 3, 5)");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("2 In (1, 3, 5)");
		assertFalse(rf.satisfiesFilter(record));

		rf = new RecordFilter("'esol' IN 'resolve'");
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void not() {
		Map<String, Object> record = new HashMap<>();
		RecordFilter rf = new RecordFilter("not children Is "
				+ "\n\r\t  Empty");
		record.put("children", Arrays.asList("joseph", "sarah"));

		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("not 'pepe' IN      children ");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("not (quantity > 10)");
		record.put("quantity", 2);
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void or() {
		Map<String, Object> record = new HashMap<>();
		RecordFilter rf = new RecordFilter("balance > 0 OR quantity = 2");
		record.put("quantity", 2);
		record.put("balance", 3.50);
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void isNot() {
		Map<String, Object> record = new HashMap<>();
		RecordFilter rf = new RecordFilter("quantity is   not  \t 0");
		record.put("quantity", 20);
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void is() {
		Map<String, Object> record = new HashMap<>();
		RecordFilter rf = new RecordFilter("quantity is \r\t 0");
		record.put("quantity", 0);
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void matches() {
		Map<String, Object> record = new HashMap<>();
		RecordFilter rf = new RecordFilter("name matches 'chaper[ ]+[0-9]+'");
		record.put("name", "chaper 99");
		assertTrue(rf.satisfiesFilter(record));
		record.put("name", "chaper99");
		assertFalse(rf.satisfiesFilter(record));
	}


	@Test
	public void empty() {
		Map<String, Object> record = new HashMap<>();
		RecordFilter rf = new RecordFilter("children Is Empty");
		record.put("children", Arrays.asList("joseph", "sarah"));

		assertFalse(rf.satisfiesFilter(record));
		rf = new RecordFilter("children Is Not Empty");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("(1, 3, 5) IS NOT EMPTY");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("(1, 3, 5) Is empty");
		assertFalse(rf.satisfiesFilter(record));

		rf = new RecordFilter("() Is empty");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("'' Is empty");
		assertTrue(rf.satisfiesFilter(record));

		record.put("name", "");
		rf = new RecordFilter("name Is empty");
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void nullTest() {
		Map<String, Object> record = new HashMap<>();

		RecordFilter rf = new RecordFilter("null IS NULL");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("name Is Null");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("name = Null");
		assertTrue(rf.satisfiesFilter(record));

		record.put("name", "");
		rf = new RecordFilter("'name' IS NULL");
		assertFalse(rf.satisfiesFilter(record));
	}

	@Test
	public void arithmetic() {
		Map<String, Object> record = new HashMap<>();

		RecordFilter rf = new RecordFilter("5 + 6 > 10");
		assertTrue(rf.satisfiesFilter(record));

		record.put("credit", 10.59);
		record.put("debit", 5.76);
		rf = new RecordFilter("(credit - debit) > 0");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("(credit + debit) = 16.35");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("(credit / debit) > 1.83");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("(credit * debit) > 60");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("(credit % debit) = 4.83");
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void stringConcat() {
		Map<String, Object> record = new HashMap<>();
		record.put("name", "Emily");
		record.put("Last Name", "Dickinson");

		RecordFilter rf = new RecordFilter("name || ' ' || \"Last Name\" = 'Emily Dickinson'");
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void booleans() {
		Map<String, Object> record = new HashMap<>();
		record.put("tall", true);
		record.put("short", false);
		record.put("true", true);

		RecordFilter rf = new RecordFilter("tall is true And short is false");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("(tall = true) And (short is false)");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("\"true\" = true");
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void nestedFields() {
		Map<String, Object> record = new HashMap<>();
		record.put("parent|name", "Emily");

		RecordFilter rf = new RecordFilter("\"parent|name\" = 'Emily'");
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void sum() {
		Map<String, Object> record = new HashMap<>();
		record.put("readings", Arrays.asList(3, 5));

		RecordFilter rf = new RecordFilter("SUM(readings) = 8");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("SUM(3, 5.0, readings) = 16.0");
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void count() {
		Map<String, Object> record = new HashMap<>();
		record.put("readings", Arrays.asList(3, 5));

		RecordFilter rf = new RecordFilter("COUNT(readings) = 2");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("COUNT(3, 5.0, readings) = 4");
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void avg() {
		Map<String, Object> record = new HashMap<>();
		record.put("readings", Arrays.asList(3, 5));

		RecordFilter rf = new RecordFilter("avg (readings) = 4");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("Avg(3, 5, readings) = 4");
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void min() {
		Map<String, Object> record = new HashMap<>();
		record.put("readings", Arrays.asList(3, 5));

		RecordFilter rf = new RecordFilter("min(readings) = 3");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("MIN(3, 5, readings) = 3");
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void max() {
		Map<String, Object> record = new HashMap<>();
		record.put("readings", Arrays.asList(3, 5));

		RecordFilter rf = new RecordFilter("max(readings) = 5");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("MAX(3, 5, readings) = 5");
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void len() {
		Map<String, Object> record = new HashMap<>();
		record.put("name", "Emily");
		record.put("lname", "Dickinson");

		RecordFilter rf = new RecordFilter("CHAR_LENGTH(name) = 5");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("CHARACTER_LENGTH(name) = 5");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("CHAR_LENGTH(name || lname) = 14");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("CHARACTER_LENGTH(name || lname) = 14");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("CHARACTER_LENGTH('Emily Dickinson') = 15");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("CHARACTER_LENGTH('Emily' || ' ' || lname) = 15");
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void sqrt() {
		Map<String, Object> record = new HashMap<>();
		record.put("value", 9);
		RecordFilter rf = new RecordFilter("SQRT(value) = 3");
		assertTrue(rf.satisfiesFilter(record));

		rf = new RecordFilter("SQRT(value*value) = value");
		assertTrue(rf.satisfiesFilter(record));
	}

	@Test
	public void compare() {
		Map<String, Object> record = new HashMap<>();
		record.put("strValue1", "abc");
		record.put("strValue2", "abcd");
		record.put("intValue1", 123);
		record.put("intValue2", 124);
		record.put("fltValue1", 1.23);
		record.put("fltValue2", 1.24);

		compareWith(record, ">=", true);
		compareWith(record, ">", true);
		compareWith(record, "<", false);
		compareWith(record, "<=", false);
		compareWith(record, "=", false);
	}

	private void compareWith(Map<String, Object> record, String op, boolean expected) {
		RecordFilter rf = new RecordFilter("strValue2 " + op + " strValue1");
		assertTrue(rf.satisfiesFilter(record) == expected);

		rf = new RecordFilter("intValue2 " + op + " intValue1");
		assertTrue(rf.satisfiesFilter(record) == expected);

		rf = new RecordFilter("fltValue2 " + op + " fltValue1");
		assertTrue(rf.satisfiesFilter(record) == expected);
	}
}
