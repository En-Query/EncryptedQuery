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
package org.enquery.encryptedquery.filter;

import java.util.Map;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.filter.antlr4.PredicateLexer;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.ExpressionContext;
import org.enquery.encryptedquery.filter.eval.ErrorListener;
import org.enquery.encryptedquery.filter.eval.Evaluator;
import org.joo.libra.Predicate;
import org.joo.libra.PredicateContext;
import org.joo.libra.sql.node.ExpressionNode;

/**
 * Filter input records using SQL-like expression
 */
public class RecordFilter {

	private ExpressionContext expression;
	private Predicate predicate;

	/**
	 * 
	 */
	public RecordFilter(String filterExp) {
		CharStream stream = CharStreams.fromString(filterExp);
		PredicateLexer lexer = new PredicateLexer(stream);
		lexer.removeErrorListeners();
		lexer.addErrorListener(new ErrorListener());

		CommonTokenStream tokens = new CommonTokenStream(lexer);
		PredicateParser parser = new PredicateParser(tokens);
		parser.removeErrorListeners();
		parser.addErrorListener(new ErrorListener());

		expression = parser.expression();
		Evaluator evaluator = new Evaluator();
		ExpressionNode node = evaluator.visit(expression);
		Validate.notNull(node, "Error evaluating filter expression.");
		predicate = node.buildPredicate();
		Validate.notNull(predicate, "Error evaluating filter expression.");
	}

	public boolean satisfiesFilter(Map<String, Object> record) {
		PredicateContext context = new PredicateContext(record);
		return predicate.satisfiedBy(context);
	}


}
