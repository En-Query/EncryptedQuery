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
package org.enquery.encryptedquery.json;

import java.io.IOException;
import java.util.HashMap;

import org.enquery.encryptedquery.data.DataSchema;

import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;

public class MapWithSchemaInstantiator extends ValueInstantiator.Base {
	private final DataSchema schema;

	protected MapWithSchemaInstantiator(DataSchema schema) {
		super(MapWithSchema.class);
		this.schema = schema;
	}

	@Override
	public boolean canCreateUsingDefault() {
		return true;
	}

	@Override
	public Object createUsingDefault(DeserializationContext derContext) throws IOException {
		final JsonStreamContext parsingContext = derContext.getParser().getParsingContext();
		boolean includeAll = fullObjectRequested(parsingContext, null);
		if (includeAll) {
			return new HashMap<String, Object>();
		}
		final String prefix = calcPrefix(parsingContext, null);
		return new MapWithSchema(schema, prefix);
	}

	/**
	 * True, if current or any ancestor key is named in the data schema, false otherwise.
	 * 
	 * @param ctx
	 * @param prefix
	 * @return
	 */
	private boolean fullObjectRequested(final JsonStreamContext ctx, final String prefix) {
		if (ctx == null) return false;

		final String currentName = ctx.getCurrentName();
		boolean result = fullObjectRequested(ctx.getParent(), currentName);
		if (result) return true;

		final String fullName = concat(currentName, prefix);
		if (fullName != null) {
			result = schema.elementByName(fullName) != null;
		}

		return result;
	}

	/**
	 * @param parsingContext
	 * @return
	 */
	private String calcPrefix(final JsonStreamContext context, String prefix) {
		if (context == null) return prefix;
		return calcPrefix(context.getParent(), concat(context.getCurrentName(), prefix));
	}

	private static String concat(String prefix, String key) {
		// TODO: escape the | character if present in the key
		// key.replaceAll("\\", "\\");
		// key.replaceAll("\\|", "\\|");
		if (prefix == null || prefix.length() == 0) return key;
		if (key == null) return prefix;
		return String.format("%s|%s", prefix, key);
	}


}
