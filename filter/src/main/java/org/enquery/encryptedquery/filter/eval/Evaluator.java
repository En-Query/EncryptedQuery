/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package org.enquery.encryptedquery.filter.eval;

import java.text.NumberFormat;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.enquery.encryptedquery.filter.antlr4.PredicateParser.AndExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.AvgExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.BooleanExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.CompareExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.CountExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.CurrentDateExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.CurrentTimestampExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.DateTimeExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.EmptyExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.InExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.LenExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.ListCommaExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.ListFactorExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.MatchesExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.MathExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.MaxExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.MinExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.NotEmptyExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.NotEqualExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.NotExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.NullExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.NumberExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.OrExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.ParenExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.SqrtExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.StrConcatExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.StringExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.SumExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.VariableExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.WrapListExprContext;
import org.enquery.encryptedquery.filter.antlr4.PredicateParserBaseVisitor;
import org.enquery.encryptedquery.filter.node.AvgExpressionNode;
import org.enquery.encryptedquery.filter.node.CharacterLenExpressionNode;
import org.enquery.encryptedquery.filter.node.CompareExpressionNode;
import org.enquery.encryptedquery.filter.node.CountExpressionNode;
import org.enquery.encryptedquery.filter.node.EmptyExpressionNode;
import org.enquery.encryptedquery.filter.node.MatchesExpressionNode;
import org.enquery.encryptedquery.filter.node.MathExpressionNode;
import org.enquery.encryptedquery.filter.node.MaxExpressionNode;
import org.enquery.encryptedquery.filter.node.MinExpressionNode;
import org.enquery.encryptedquery.filter.node.NotEmptyExpressionNode;
import org.enquery.encryptedquery.filter.node.SqrtExpressionNode;
import org.enquery.encryptedquery.filter.node.StringConcatExpressionNode;
import org.enquery.encryptedquery.filter.node.SumExpressionNode;
import org.enquery.encryptedquery.filter.node.TemporalExpressionNode;
import org.enquery.encryptedquery.filter.node.TimestampExpressionNode;
import org.joo.libra.common.HasList;
import org.joo.libra.common.HasValue;
import org.joo.libra.sql.node.AndExpressionNode;
import org.joo.libra.sql.node.BooleanExpressionNode;
import org.joo.libra.sql.node.ExpressionNode;
import org.joo.libra.sql.node.InCompareExpressionNode;
import org.joo.libra.sql.node.ListExpressionNode;
import org.joo.libra.sql.node.ListItemExpressionNode;
import org.joo.libra.sql.node.NotExpressionNode;
import org.joo.libra.sql.node.NumberExpressionNode;
import org.joo.libra.sql.node.ObjectExpressionNode;
import org.joo.libra.sql.node.OrExpressionNode;
import org.joo.libra.sql.node.StringExpressionNode;
import org.joo.libra.sql.node.TempVariableExpressionNode;
import org.joo.libra.sql.node.VariableExpressionNode;
import org.joo.libra.support.exceptions.MalformedSyntaxException;

/**
 *
 */
public class Evaluator extends PredicateParserBaseVisitor<ExpressionNode> {

	@Override
	public ExpressionNode visitVariableExpr(VariableExprContext ctx) {
		VariableExpressionNode node = new VariableExpressionNode();
		String value = ctx.getText();

		// variables can be quoted, remove the quotes if so
		if (value.startsWith("\"")) {
			value = value.substring(1, value.length() - 1);
		}
		node.setVariableName(value);
		return node;
	}

	@Override
	public ExpressionNode visitNumberExpr(NumberExprContext ctx) {
		NumberExpressionNode node = new NumberExpressionNode();
		try {
			Number num = NumberFormat.getInstance().parse(ctx.getText());
			node.setValue(num);
		} catch (ParseException e) {
			throw new RuntimeException("Error parsing number: " + ctx.getText(), e);
		}
		return node;
	}

	@Override
	public ExpressionNode visitCurrentTimestampExpr(CurrentTimestampExprContext ctx) {
		return new TemporalExpressionNode(Instant.now());
	}

	@Override
	public ExpressionNode visitCurrentDateExpr(CurrentDateExprContext ctx) {
		return new TemporalExpressionNode(Instant.now().truncatedTo(ChronoUnit.DAYS));
	}

	@Override
	public ExpressionNode visitBooleanExpr(BooleanExprContext ctx) {
		BooleanExpressionNode node = new BooleanExpressionNode();
		boolean value = Boolean.valueOf(ctx.getText().toLowerCase());
		node.setValue(value);
		return node;
	}

	@SuppressWarnings("unchecked")
	@Override
	public ExpressionNode visitCompareExpr(CompareExprContext ctx) {
		CompareExpressionNode node = new CompareExpressionNode();
		ExpressionNode left = visit(ctx.left);
		ExpressionNode right = visit(ctx.right);

		node.setLeft((HasValue<Object>) left);
		node.setRight((HasValue<Object>) right);
		node.setOp(ctx.op.getType());

		return node;
	}

	@SuppressWarnings("unchecked")
	@Override
	public ExpressionNode visitNotEqualExpr(NotEqualExprContext ctx) {
		CompareExpressionNode node = new CompareExpressionNode(r -> r != 0);
		ExpressionNode left = visit(ctx.left);
		ExpressionNode right = visit(ctx.right);
		node.setLeft((HasValue<Object>) left);
		node.setRight((HasValue<Object>) right);
		return node;
	}

	@Override
	public ExpressionNode visitAndExpr(AndExprContext ctx) {
		AndExpressionNode node = new AndExpressionNode();
		node.setLeft(visit(ctx.left));
		node.setRight(visit(ctx.right));
		return node;
	}

	@Override
	public ExpressionNode visitStringExpr(StringExprContext ctx) {
		String value = ctx.getText();
		value = value.substring(1, value.length() - 1);
		StringExpressionNode node = new StringExpressionNode();
		node.setValue(value);
		return node;
	}

	@Override
	public ExpressionNode visitDateTimeExpr(DateTimeExprContext ctx) {
		String value = ctx.value.getText();
		value = value.substring(1, value.length() - 1);
		return new TimestampExpressionNode(value);
		// // need to be ISO8601 timestamp
		// try {
		// long longDate = ISO8601DateParser.getLongDate(value);
		// NumberExpressionNode node = new NumberExpressionNode();
		// node.setValue(longDate);
		// return node;
		// } catch (DateTimeParseException e) {
		// // Ok, it is not a valid timestamp
		// }
	}

	@Override
	public ExpressionNode visitEmptyExpr(EmptyExprContext ctx) {
		EmptyExpressionNode node = new EmptyExpressionNode();
		node.setInnerNode(visit(ctx.left));
		return node;
	}


	@Override
	public ExpressionNode visitNotEmptyExpr(NotEmptyExprContext ctx) {
		NotEmptyExpressionNode node = new NotEmptyExpressionNode();
		node.setInnerNode(visit(ctx.left));
		return node;
	}

	@Override
	public ExpressionNode visitListFactorExpr(ListFactorExprContext ctx) {
		ListItemExpressionNode node = new ListItemExpressionNode();
		ExpressionNode innerNode = visitChildren(ctx);
		if (!(innerNode instanceof HasValue))
			throw new MalformedSyntaxException("Inner node must be value type: " + ctx.getChild(0).getText());
		node.getInnerNode().add(innerNode);
		return node;
	}

	@Override
	public ExpressionNode visitListCommaExpr(ListCommaExprContext ctx) {
		ListItemExpressionNode node = new ListItemExpressionNode();
		ListItemExpressionNode left = (ListItemExpressionNode) visit(ctx.left);
		ListItemExpressionNode right = (ListItemExpressionNode) visit(ctx.right);
		node.getInnerNode().addAll(left.getInnerNode());
		node.getInnerNode().addAll(right.getInnerNode());
		return node;
	}

	@Override
	public ExpressionNode visitWrapListExpr(WrapListExprContext ctx) {
		ListExpressionNode node = new ListExpressionNode();
		if (ctx.item != null)
			node.setListItem((ListItemExpressionNode) visit(ctx.item));
		return node;
	}

	@Override
	public ExpressionNode visitNotExpr(NotExprContext ctx) {
		NotExpressionNode node = new NotExpressionNode();
		node.setInnerNode(visitChildren(ctx));
		return node;
	}

	@Override
	public ExpressionNode visitParenExpr(ParenExprContext ctx) {
		return visit(ctx.getChild(1));
	}

	@Override
	public ExpressionNode visitOrExpr(OrExprContext ctx) {
		OrExpressionNode node = new OrExpressionNode();
		node.setLeft(visit(ctx.left));
		node.setRight(visit(ctx.right));
		return node;
	}


	@Override
	public ExpressionNode visitInExpr(InExprContext ctx) {
		InCompareExpressionNode node = new InCompareExpressionNode();
		node.setLeft((HasValue<?>) visit(ctx.left));
		node.setRight((HasValue<?>) visit(ctx.right));
		node.setOp(ctx.op.getType());
		return node;
	}


	@SuppressWarnings("unchecked")
	@Override
	public ExpressionNode visitMatchesExpr(MatchesExprContext ctx) {
		MatchesExpressionNode node = new MatchesExpressionNode();
		node.setLeft((HasValue<String>) visit(ctx.left));
		node.setRight((HasValue<String>) visit(ctx.right));
		node.setOp(ctx.op.getType());

		if (!isStringNode(node.getLeft()) || !isStringNode(node.getRight()))
			throw new MalformedSyntaxException("Malformed syntax at visit node, string node expected");

		return node;
	}


	@SuppressWarnings("unchecked")
	@Override
	public ExpressionNode visitMathExpr(MathExprContext ctx) {
		MathExpressionNode node = new MathExpressionNode();
		node.setLeft((HasValue<Number>) visit(ctx.left));
		node.setRight((HasValue<Number>) visit(ctx.right));
		node.setOp(ctx.op.getType());
		return node;
	}

	@SuppressWarnings("unchecked")
	@Override
	public ExpressionNode visitStrConcatExpr(StrConcatExprContext ctx) {
		StringConcatExpressionNode node = new StringConcatExpressionNode();
		node.setLeft((HasValue<String>) visit(ctx.left));
		node.setRight((HasValue<String>) visit(ctx.right));
		return node;
	}

	@Override
	public ExpressionNode visitNullExpr(NullExprContext ctx) {
		ObjectExpressionNode node = new ObjectExpressionNode();
		node.setValue(null);
		return node;
	}

	@Override
	public ExpressionNode visitSumExpr(SumExprContext ctx) {
		return new SumExpressionNode((HasList) visit(ctx.inner));
	}

	@Override
	public ExpressionNode visitAvgExpr(AvgExprContext ctx) {
		return new AvgExpressionNode((HasList) visit(ctx.inner));
	}

	@Override
	public ExpressionNode visitCountExpr(CountExprContext ctx) {
		return new CountExpressionNode((HasList) visit(ctx.inner));
	}

	@Override
	public ExpressionNode visitMinExpr(MinExprContext ctx) {
		return new MinExpressionNode((HasList) visit(ctx.inner));
	}

	@Override
	public ExpressionNode visitMaxExpr(MaxExprContext ctx) {
		return new MaxExpressionNode((HasList) visit(ctx.inner));
	}

	@SuppressWarnings("unchecked")
	@Override
	public ExpressionNode visitLenExpr(LenExprContext ctx) {
		return new CharacterLenExpressionNode((HasValue<String>) visit(ctx.inner));
	}

	@SuppressWarnings("unchecked")
	@Override
	public ExpressionNode visitSqrtExpr(SqrtExprContext ctx) {
		return new SqrtExpressionNode((HasValue<Number>) visit(ctx.inner));
	}

	private boolean isStringNode(final Object node) {
		return node instanceof StringExpressionNode || node instanceof VariableExpressionNode
				|| node instanceof TempVariableExpressionNode;
	}

}
