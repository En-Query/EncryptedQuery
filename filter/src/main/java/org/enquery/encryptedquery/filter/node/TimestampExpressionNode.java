package org.enquery.encryptedquery.filter.node;

import java.time.Instant;

import org.enquery.encryptedquery.utils.ISO8601DateParser;
import org.joo.libra.Predicate;
import org.joo.libra.PredicateContext;
import org.joo.libra.common.DerivedLiteralPredicate;
import org.joo.libra.sql.node.ValueExpressionNode;

public class TimestampExpressionNode extends ValueExpressionNode<Instant> {

	private final String stringValue;

	public TimestampExpressionNode(String value) {
		stringValue = value;
	}

	@Override
	public Predicate buildPredicate() {
		return new DerivedLiteralPredicate<>(this, value -> value != null);
	}

	@Override
	public Instant getValue() {
		return ISO8601DateParser.getInstant(stringValue);
	}

	@Override
	public Instant getValue(PredicateContext context) {
		return getValue();
	}

	@Override
	public String toString() {
		return "DATE|TIMESTAMP " + stringValue;
	}
}
