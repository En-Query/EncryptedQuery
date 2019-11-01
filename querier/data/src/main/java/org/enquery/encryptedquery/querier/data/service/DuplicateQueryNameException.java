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
package org.enquery.encryptedquery.querier.data.service;

import javax.persistence.PersistenceException;

import org.enquery.encryptedquery.querier.data.entity.jpa.Query;

/**
 *
 */
public class DuplicateQueryNameException extends PersistenceException {

	private static final long serialVersionUID = -3776894954437306234L;

	/**
	 * 
	 */
	public DuplicateQueryNameException() {}

	/**
	 * 
	 */
	public DuplicateQueryNameException(Query query) {
		super(String.format("Unique constraint violation. Query name not unique: '%s'.", query));
	}

}
