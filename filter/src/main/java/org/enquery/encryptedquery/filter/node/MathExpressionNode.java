package org.enquery.encryptedquery.filter.node;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.filter.antlr4.PredicateLexer;
import org.joo.libra.Predicate;
import org.joo.libra.PredicateContext;
import org.joo.libra.common.DerivedLiteralPredicate;
import org.joo.libra.common.HasValue;
import org.joo.libra.sql.node.AbstractBinaryOpExpressionNode;
import org.joo.libra.support.GenericComparator;

public class MathExpressionNode extends AbstractBinaryOpExpressionNode<HasValue<Number>> implements HasValue<Number> {

	@Override
	public Predicate buildPredicate() {
		return new DerivedLiteralPredicate<>(this, value -> GenericComparator.compareNumber(value, 0) != 0);
	}

	@Override
	public Number getValue(final PredicateContext context) {
		final Number left = getLeft().getValue(context);
		Validate.notNull(left);
		final Number right = getRight().getValue(context);
		Validate.notNull(right);

		switch (getOp()) {
			case PredicateLexer.PLUS:
				return add(left, right);
			case PredicateLexer.MINUS:
				return substract(left, right);
			case PredicateLexer.TIMES:
				return multiply(left, right);
			case PredicateLexer.DIVIDE:
				return divide(left, right);
			case PredicateLexer.MODULO:
				return modulo(left, right);
			default:
				return null;
		}
	}

	/**
	 * @param left
	 * @param right
	 * @return
	 */
	private Number modulo(Number left, Number right) {
		if (NumberUtils.isOrdinal(left) || NumberUtils.isOrdinal(right)) {
			return left.longValue() % right.longValue();
		} else {
			return left.doubleValue() % right.doubleValue();
		}
	}

	/**
	 * @param left
	 * @param right
	 * @return
	 */
	private Number divide(Number left, Number right) {
		if (NumberUtils.isOrdinal(left) || NumberUtils.isOrdinal(right)) {
			return left.longValue() / right.longValue();
		} else {
			return left.doubleValue() / right.doubleValue();
		}
	}

	/**
	 * @param left
	 * @param right
	 * @return
	 */
	private Number multiply(Number left, Number right) {
		if (NumberUtils.isOrdinal(left) || NumberUtils.isOrdinal(right)) {
			return left.longValue() * right.longValue();
		} else {
			return left.doubleValue() * right.doubleValue();
		}
	}

	/**
	 * @param left
	 * @param right
	 * @return
	 */
	private Number substract(Number left, Number right) {
		if (NumberUtils.isOrdinal(left) || NumberUtils.isOrdinal(right)) {
			return left.longValue() - right.longValue();
		} else {
			return left.doubleValue() - right.doubleValue();
		}
	}

	/**
	 * @param left
	 * @param right
	 * @return
	 */
	private Number add(Number left, Number right) {
		if (NumberUtils.isOrdinal(left) || NumberUtils.isOrdinal(right)) {
			return left.longValue() + right.longValue();
		} else {
			return left.doubleValue() + right.doubleValue();
		}
	}

	@Override
	public String toString() {
		return getOpAsString() + "(" + getLeft() + "," + getRight() + ")";
	}

	private String getOpAsString() {
		switch (getOp()) {
			case PredicateLexer.PLUS:
				return "PLUS";
			case PredicateLexer.MINUS:
				return "MINUS";
			case PredicateLexer.TIMES:
				return "TIMES";
			case PredicateLexer.DIVIDE:
				return "DIVIDE";
			case PredicateLexer.MODULO:
				return "MODULO";
			default:
				return null;
		}
	}
}
