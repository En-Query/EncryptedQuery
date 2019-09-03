package org.enquery.encryptedquery.filter.node;

import org.apache.commons.lang3.Validate;
import org.joo.libra.Predicate;
import org.joo.libra.PredicateContext;
import org.joo.libra.common.DerivedLiteralPredicate;
import org.joo.libra.common.HasValue;
import org.joo.libra.sql.node.ExpressionNode;
import org.joo.libra.support.GenericComparator;

public class CharacterLenExpressionNode implements ExpressionNode, HasValue<Number> {

	private final HasValue<String> inner;

	public CharacterLenExpressionNode(HasValue<String> inner) {
		Validate.notNull(inner);
		this.inner = inner;
	}

	@Override
	public Predicate buildPredicate() {
		return new DerivedLiteralPredicate<>(this, value -> GenericComparator.compareNumber(value, 0) != 0);
	}

	@Override
	public Number getValue(final PredicateContext context) {
		String arg = inner.getValue(context);
		if (arg == null)
			throw new IllegalArgumentException("CHARACTER_LENGTH function must have one argument.");

		return arg.length();
	}

	@Override
	public String toString() {
		return "CHARACTER_LENGTH(" + inner + ")";
	}
}
