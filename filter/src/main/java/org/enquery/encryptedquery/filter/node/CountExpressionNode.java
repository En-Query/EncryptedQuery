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

public class CountExpressionNode implements ExpressionNode, HasValue<Number> {

	private final HasList inner;

	public CountExpressionNode(HasList inner) {
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
			throw new IllegalArgumentException("COUNT function must have at least one argument.");

		int result = 0;
		for (Object item : args) {
			result = count(result, item);
		}
		return result;
	}

	/**
	 * @param result
	 * @param item
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	private int count(int result, Object item) {
		if (item instanceof List) {
			return countList(result, (List) item);
		} else {
			return result + 1;
		}
	}

	/**
	 * @param result
	 * @param item
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	private int countList(int initialValue, List list) {
		int result = initialValue;
		for (Object item : list) {
			result = count(result, item);
		}
		return result;
	}

	@Override
	public String toString() {
		return "COUNT(" + inner + ")";
	}
}
