package org.enquery.encryptedquery.filter.node;


import org.enquery.encryptedquery.filter.antlr4.PredicateLexer;
import org.enquery.encryptedquery.filter.predicate.GenericRelationalOperatorPredicate;
import org.joo.libra.Predicate;
import org.joo.libra.common.HasValue;
import org.joo.libra.sql.node.AbstractBinaryOpExpressionNode;

public class CompareExpressionNode extends AbstractBinaryOpExpressionNode<HasValue<Object>> {

	private java.util.function.Predicate<Integer> calcResult;

	/**
	 * 
	 */
	public CompareExpressionNode() {}

	/**
	 * 
	 */
	public CompareExpressionNode(java.util.function.Predicate<Integer> calcResult) {
		this.calcResult = calcResult;
	}



	@Override
	public Predicate buildPredicate() {
		if (calcResult != null) {
			return new GenericRelationalOperatorPredicate(getLeft(), getRight(), calcResult);
		}

		switch (getOp()) {
			case PredicateLexer.GREATER_THAN:
				return new GenericRelationalOperatorPredicate(getLeft(), getRight(), r -> r > 0);
			case PredicateLexer.GREATER_THAN_EQUALS:
				return new GenericRelationalOperatorPredicate(getLeft(), getRight(), r -> r >= 0);
			case PredicateLexer.LESS_THAN:
				return new GenericRelationalOperatorPredicate(getLeft(), getRight(), r -> r < 0);
			case PredicateLexer.LESS_THAN_EQUALS:
				return new GenericRelationalOperatorPredicate(getLeft(), getRight(), r -> r <= 0);
			case PredicateLexer.EQUALS:
			case PredicateLexer.IS:
				return new GenericRelationalOperatorPredicate(getLeft(), getRight(), r -> r == 0);
			case PredicateLexer.NOT_EQUALS:
				return new GenericRelationalOperatorPredicate(getLeft(), getRight(), r -> r != 0);
			default:
				throw new IllegalArgumentException("Unknown relational operator.");
		}
	}
}
