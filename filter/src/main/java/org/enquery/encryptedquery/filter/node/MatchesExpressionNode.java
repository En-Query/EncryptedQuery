package org.enquery.encryptedquery.filter.node;

import org.joo.libra.Predicate;
import org.joo.libra.common.HasValue;
import org.joo.libra.sql.node.AbstractBinaryOpExpressionNode;
import org.joo.libra.text.MatchPredicate;

public class MatchesExpressionNode extends AbstractBinaryOpExpressionNode<HasValue<String>> {

	@Override
	public Predicate buildPredicate() {
		return new MatchPredicate(getLeft(), getRight());
	}
}
