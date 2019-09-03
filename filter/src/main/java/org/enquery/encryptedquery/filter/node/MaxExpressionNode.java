package org.enquery.encryptedquery.filter.node;

import java.util.List;

import org.apache.commons.lang3.Validate;
import org.joo.libra.Predicate;
import org.joo.libra.PredicateContext;
import org.joo.libra.common.DerivedLiteralPredicate;
import org.joo.libra.common.HasList;
import org.joo.libra.common.HasValue;
import org.joo.libra.sql.node.ExpressionNode;
import org.joo.libra.support.GenericComparator;

public class MaxExpressionNode implements ExpressionNode, HasValue<Number> {

	private final HasList inner;

	public MaxExpressionNode(HasList inner) {
		Validate.notNull(inner);
		this.inner = inner;
	}

	@Override
	public Predicate buildPredicate() {
		return new DerivedLiteralPredicate<>(this, value -> GenericComparator.compareNumber(value, 0) != 0);
	}

	@Override
	public Number getValue(final PredicateContext context) {
		Object[] args = inner.getValueAsArray(context);
		if (args == null || args.length == 0)
			throw new IllegalArgumentException("MAX function must have at least one argument.");

		Number result = null;
		for (Object item : args) {
			result = max(result, item);
		}
		return result;
	}

	/**
	 * @param result
	 * @param item
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	private Number max(Number result, Object item) {
		if (item instanceof List) {
			return maxList(result, (List) item);
		} else {
			Validate.isInstanceOf(Number.class, item);
			return maxScalar(result, (Number) item);
		}
	}

	/**
	 * @param result
	 * @param item
	 * @return
	 */
	private Number maxScalar(Number initialValue, Number value) {
		if (initialValue == null) return value;

		if (NumberUtils.isOrdinal(initialValue) || NumberUtils.isOrdinal(value)) {
			return Math.max(initialValue.longValue(), value.longValue());
		}
		return Math.max(initialValue.doubleValue(), value.doubleValue());
	}

	/**
	 * @param result
	 * @param item
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	private Number maxList(Number initialValue, List list) {
		Number result = initialValue;
		for (Object item : list) {
			result = max(result, item);
		}
		return result;
	}

	@Override
	public String toString() {
		return "MAX(" + inner + ")";
	}
}
