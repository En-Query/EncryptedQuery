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
package org.enquery.encryptedquery.filter.predicate;

import java.time.Instant;
import java.util.function.Predicate;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.filter.node.NumberUtils;
import org.joo.libra.common.BinaryPredicate;
import org.joo.libra.common.HasValue;

/**
 *
 */
public class GenericRelationalOperatorPredicate extends BinaryPredicate<Object, Object> {

	private Predicate<Integer> calcResult;

	/**
	 * @param one
	 * @param other
	 */
	public GenericRelationalOperatorPredicate(HasValue<Object> one, HasValue<Object> other, Predicate<Integer> calcResult) {
		super(one, other);
		this.calcResult = calcResult;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.joo.libra.common.BinaryPredicate#doSatisifiedBy(java.lang.Object, java.lang.Object)
	 */
	@Override
	protected boolean doSatisifiedBy(Object one, Object other) {
		Validate.notNull(one);
		Validate.notNull(other);

		if (one instanceof String && other instanceof String) {
			return compare((String) one, (String) other);
		} else if (one instanceof Number && other instanceof Number) {
			return compare((Number) one, (Number) other);
		} else if (one instanceof Boolean && other instanceof Boolean) {
			return calcResult.test(Boolean.compare((Boolean) one, (Boolean) other));
		} else if (one instanceof Instant && other instanceof Instant) {
			return calcResult.test(compare((Instant) one, (Instant) other));
		}
		throw new IllegalArgumentException(
				String.format("Relational operators must have arguments of the same type."
						+ "One is '%s', and other is '%s'.", one.getClass().getName(),
						other.getClass().getName()));
	}

	/**
	 * @param one
	 * @param other
	 * @return
	 */
	private Integer compare(Instant one, Instant other) {
		return one.compareTo(other);
	}

	/**
	 * @param one
	 * @param other
	 * @return
	 */
	private boolean compare(Number one, Number other) {
		int compareResult = 0;
		if (NumberUtils.isOrdinal(one) && NumberUtils.isOrdinal(other)) {
			compareResult = Long.compare(one.longValue(), other.longValue());
		} else {
			compareResult = Double.compare(one.doubleValue(), other.doubleValue());
		}

		return calcResult.test(compareResult);
	}

	/**
	 * @param one
	 * @param other
	 * @return
	 */
	private boolean compare(String one, String other) {
		return calcResult.test(one.compareTo(other));
	}

}
