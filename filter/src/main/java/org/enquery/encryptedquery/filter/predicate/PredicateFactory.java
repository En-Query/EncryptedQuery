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
package org.enquery.encryptedquery.filter.predicate;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.filter.antlr4.PredicateLexer;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser;
import org.enquery.encryptedquery.filter.antlr4.PredicateParser.ExpressionContext;
import org.enquery.encryptedquery.filter.error.ErrorListener;
import org.enquery.encryptedquery.filter.eval.Evaluator;
import org.joo.libra.Predicate;
import org.joo.libra.sql.node.ExpressionNode;

/**
 *
 */
public class PredicateFactory {
	public static Predicate make(String filterExp, DataSchema dataSchema, ErrorListener listener) {
		CharStream stream = CharStreams.fromString(filterExp);
		PredicateLexer lexer = new PredicateLexer(stream);

		CommonTokenStream tokens = new CommonTokenStream(lexer);
		PredicateParser parser = new PredicateParser(tokens);

		if (listener != null) {
			lexer.removeErrorListeners();
			lexer.addErrorListener(listener);
			parser.removeErrorListeners();
			parser.addErrorListener(listener);
		}

		ExpressionContext expression = parser.expression();
		Evaluator evaluator = new Evaluator(dataSchema, listener);
		ExpressionNode node = evaluator.visit(expression);
		if (node == null) return null;
		return node.buildPredicate();
	}
}
