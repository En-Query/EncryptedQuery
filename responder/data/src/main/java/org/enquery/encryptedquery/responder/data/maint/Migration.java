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
package org.enquery.encryptedquery.responder.data.maint;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.ops4j.pax.jdbc.hook.PreHook;
import org.osgi.service.component.annotations.Component;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.lockservice.LockServiceFactory;
import liquibase.lockservice.StandardLockService;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;

@Component(property = "name=responderDB")
public class Migration implements PreHook {

	@Override
	public void prepare(DataSource dataSource) throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			prepare(connection);
		} catch (LiquibaseException e) {
			throw new RuntimeException(e);
		}
	}

	private void prepare(Connection connection) throws DatabaseException, LiquibaseException {
		Database db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
		LockServiceFactory.getInstance().register(new StandardLockService());
		ClassLoader classLoader = this.getClass().getClassLoader();
		ResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor(classLoader);
		Liquibase liquibase = new Liquibase("db/changesets.xml", resourceAccessor, db);
		liquibase.update(new Contexts());
	}
}
