package org.enquery.encryptedquery.filter.node;

import org.joo.libra.Predicate;
import org.joo.libra.common.HasValue;
import org.joo.libra.logic.NotPredicate;
import org.joo.libra.sql.node.UnaryExpressionNode;
import org.joo.libra.text.IsEmptyPredicate;

public class NotEmptyExpressionNode extends UnaryExpressionNode {

	@Override
	public Predicate buildPredicate() {
		return new NotPredicate(
				new IsEmptyPredicate((HasValue<?>) getInnerNode()));
	}
}
