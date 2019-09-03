package org.enquery.encryptedquery.filter.node;

import org.joo.libra.Predicate;
import org.joo.libra.PredicateContext;
import org.joo.libra.common.DerivedLiteralPredicate;
import org.joo.libra.common.HasValue;
import org.joo.libra.sql.node.AbstractBinaryOpExpressionNode;

public class StringConcatExpressionNode extends AbstractBinaryOpExpressionNode<HasValue<String>>
		implements HasValue<String> {

	@Override
	public Predicate buildPredicate() {
		return new DerivedLiteralPredicate<>(this, value -> value != null && !value.isEmpty());
	}

	@Override
	public String getValue(final PredicateContext context) {
		String left = String.valueOf(getLeft().getValue(context));
		String right = String.valueOf(getRight().getValue(context));
		return left + right;
	}

	@Override
	public String toString() {
		return getLeft() + " + " + getRight();
	}
}
