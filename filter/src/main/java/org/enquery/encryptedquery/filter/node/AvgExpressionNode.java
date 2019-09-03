package org.enquery.encryptedquery.filter.node;

import org.apache.commons.lang3.Validate;
import org.joo.libra.Predicate;
import org.joo.libra.PredicateContext;
import org.joo.libra.common.DerivedLiteralPredicate;
import org.joo.libra.common.HasList;
import org.joo.libra.common.HasValue;
import org.joo.libra.sql.node.ExpressionNode;
import org.joo.libra.support.GenericComparator;

public class AvgExpressionNode implements ExpressionNode, HasValue<Number> {

	private final HasList inner;

	public AvgExpressionNode(HasList inner) {
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
			throw new IllegalArgumentException("AVG function must have at least one argument.");

		SumExpressionNode sumNode = new SumExpressionNode(inner);
		Number sum = sumNode.getValue(context);

		CountExpressionNode countNode = new CountExpressionNode(inner);
		Number count = countNode.getValue(context);

		if (NumberUtils.isOrdinal(sum)) {
			return sum.longValue() / count.longValue();
		}

		return sum.doubleValue() / count.longValue();
	}

	@Override
	public String toString() {
		return "AVG(" + inner + ")";
	}
}
