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
package org.enquery.encryptedquery.filter.error;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * An error listener that captures errors not throwing exceptions
 */
public class ErrorListener extends BaseErrorListener {

	private List<String> errors = new ArrayList<>();

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.antlr.v4.runtime.ANTLRErrorListener#syntaxError(org.antlr.v4.runtime.Recognizer,
	 * java.lang.Object, int, int, java.lang.String, org.antlr.v4.runtime.RecognitionException)
	 */
	@Override
	public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
		errors.add(makeMsg(msg, line, charPositionInLine));
	}

	public void semanticError(ParserRuleContext context, String error) {
		errors.add(makeMsg(context, error));
	}

	private String makeMsg(ParserRuleContext context, String error) {
		int line = context.start.getLine();
		int col = context.start.getCharPositionInLine();
		return makeMsg(error, line, col);
	}

	private String makeMsg(String error, int line, int col) {
		return String.format("Line: %d, Col: %d, Error: %s",
				line,
				col,
				error);
	}

	public List<String> getErrors() {
		return errors;
	}

}
