Responder
=====================================================================================

Building
------------

mvn clean install


Building with Integration Tests using embedded Derby Database
---------------------------------------------------------------

mvn -P itests clean install


Building with Integration Tests using external MariaDB Database
---------------------------------------------------------------

mvn -P itests,mariadb clean install

The MariaDB server host address is defaulted to: mariadb.local
Update your /etc/hosts file with the actual IP.
Example:
	192.168.56.101  mariadb.local


Running
-------------------------------------------------------------------------------------------
After project is built, the distribution project generates two distributions:

	 /dist/target/encryptedquery-responder-dist-derbydb-${project.version}.tar.gz
	 /dist/target/encryptedquery-responder-dist-mariadb--${project.version}.tar.gz
	
One for MariaDB, and another for DerbyDB.

Expand the archive corresponding to the Database engine of your preference:

	tar -xf <DISTRIBUTION-ARCHIVE>
	
Run interactively:

	$ bin/karaf

Run in the background:

	$ bin/start
	
Connect to an instance running in the background:

	$ bin/client
	
