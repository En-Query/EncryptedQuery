package org.enquery.encryptedquery.filter.node;

import java.time.Instant;

import org.apache.commons.lang3.Validate;
import org.joo.libra.Predicate;
import org.joo.libra.PredicateContext;
import org.joo.libra.common.DerivedLiteralPredicate;
import org.joo.libra.sql.node.ValueExpressionNode;

public class TemporalExpressionNode extends ValueExpressionNode<Instant> {

	public TemporalExpressionNode(Instant instant) {
		Validate.notNull(instant);
		setValue(instant);
	}

	@Override
	public Predicate buildPredicate() {
		return new DerivedLiteralPredicate<>(this, value -> value != null);
	}

	@Override
	public Instant getValue(PredicateContext context) {
		return getValue();
	}

	@Override
	public String toString() {
		return getValue().toString();
	}
}
