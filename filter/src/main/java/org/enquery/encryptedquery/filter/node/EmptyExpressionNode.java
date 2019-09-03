package org.enquery.encryptedquery.filter.node;

import org.joo.libra.Predicate;
import org.joo.libra.common.HasValue;
import org.joo.libra.sql.node.UnaryExpressionNode;
import org.joo.libra.text.IsEmptyPredicate;

public class EmptyExpressionNode extends UnaryExpressionNode {

	@Override
	public Predicate buildPredicate() {
		return new IsEmptyPredicate((HasValue<?>) getInnerNode());
	}
}
