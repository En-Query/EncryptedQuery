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
package org.enquery.encryptedquery.data;

import java.util.Arrays;
import java.util.Collection;

abstract class AbstractIndentedToString {

	static final int INDENT = 2;

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		toString(builder, 0);
		return builder.toString();
	}

	protected void toString(StringBuilder builder, int indent) {
		final String prefix = nSpaces(indent);
		builder.append(prefix)
				.append(this.getClass().getSimpleName())
				.append(" [\n");
		toStringInner(builder, indent + INDENT);
		builder.append("\n").append(prefix).append("]");
	}

	protected String nSpaces(int indent) {
		char[] spaces = new char[indent];
		Arrays.fill(spaces, ' ');
		return new String(spaces);
	}

	protected void collectionToString(Collection<? extends AbstractIndentedToString> col, StringBuilder builder, int indent) {
		int i = 0;
		for (AbstractIndentedToString item : col) {
			if (i++ > 0) builder.append(",\n");
			item.toString(builder, indent + INDENT);
		}
		if (i > 0) builder.append("\n");
	}

	abstract protected void toStringInner(StringBuilder builder, int indent);
}
