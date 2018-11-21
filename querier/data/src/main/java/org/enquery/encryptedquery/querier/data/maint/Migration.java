package org.enquery.encryptedquery.querier.data.maint;

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

@Component(property = "name=querierDB")
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
