# EncryptedQuery
## Deployment
Encrypted Query has been developed and tested on Centos 7 OS.  The application is written in Java & C and uses Apache Karaf as a launch platform.  Using REST interfaces users interact with the Querier to Configure, Generate, Schedule, & Decrypt a query.   Once Scheduled the Querier will submit the job to the Responder through REST calls to execute the query at the specified time.

Encrypted Query has been separated into two Sides (Querier and Responder)  The Querier side configures and Encrypts the query.   It is also used to Decrypt the result.   The Responder side will run the query to generate a result file.
The system is designed for the Querier and Responder to run on separate servers.  For testing they can be configured to run on the same server.   Using Apache Flink users can also query against a JDBC database.   

#### Supported Datasets
* JSON flat file
* MariaDB/MySQL Databases


### Building the Project

Refer to [Building-README.md][PlBld] for build Instructions

### Optional Software

Apache Flink (https://flink.apache.org/) for distributed processing.   Installation of Apache Flink is beyond the scope of this document.  Refer to the Apache Flink Quickstart (https://ci.apache.org/projects/flink/flink-docs-release-1.6/quickstart/setup_quickstart.html) for installation instructions.

MariaDB (https://mariadb.org/) for processing of JDBC data sources.   Installation of MariaDB is beyond the scope of this document.  Refer to the TechMint article for installing MariaDB on Centos 7 (https://www.tecmint.com/install-mariadb-in-centos-7/)

### Setup
The querier and responder are designed to be run on seperate servers but can be configured to run on the same server for testing.  For the setup below it is assumed you downloaded or cloned the application into your `/home` folder.  If the application has been downloaded somewhere else use that location in the below commands.  

##### Querier:
Create the base folders and install the querier file:
```sh
$ # Create the necessary folders
$ mkdir /opt/enquery/
$ mkdir /opt/enquery/native-libs/
$ # Copy over the native library files 
$ cp /home/encryptedquery/core/target/lib/native/libquerier.so /opt/enquery/native-libs/.
$ # Copy the querier tar file, untar it and create a soft link
$ cp /home/encryptedquery/querier/dist/target/encryptedquery-querier-dist-derbydb-2.0.0.tar.gz /opt/enquery/.
$ cd /opt/enquery/
$ tar -xvf encryptedquery-querier-dist-derbydb-2.0.0.tar.gz
$ ln -s  encryptedquery-querier-dist-2.0.0 querier
```
**Note: If you are installing both the querier and responder on the same server add another folder for separation (/opt/enquery/querier-app/) and modify the soft link accordingly.
**Note: If you would like to use MariaDB to store execution information substitute the mariadb querier file (`encryptedquery-querier-dist-mariadb-2.0.0.tar.gz`) for the derby file

###### Querier Configuration:
Update the `/opt/enquery/querier/etc/encrypted.query.querier.rest.cfg` file and add/update the responder info with your specifics:
```
context.path=/querier
responder.host=192.168.200.57
responder.port=8181
```
Update the `/opt/enquery/querier/etc/org.enquery.encryptedquery.querier.wideskies.encrypt.EncryptQuery.cfg` file and add/update the following:
```
query.encryption.thread.count=4
query.encryption.method=fastwithjni
jni.library.path=/opt/enquery/native-libs/libquerygen.so
```
*Thread Count can be set up to the number of cores you have on your server.
*query encryption methods are: (default, fast, and fastwithjni) fastwithjni uses c native libraries which speeds up encryption and is the recommended setting.
*The library name (libquerygen.so may have different suffixs depending on the os it was built on)

If you are using MariaDB as your datastore also update the /opt/enquery/querier/etc/org.ops4j.datasource-querier.cfg file and add/update the following with the specifics of your installation(database server ip, database user/password, database name):
```
# Data Source connectivity
osgi.jdbc.driver.name = mariadb
dataSourceName=querier
url=jdbc:mariadb://192.168.200.74:3306/querier?characterEncoding=UTF-8&useServerPrepStmts=true
user=enquery
password=enquery
databaseName=querier
ops4j.preHook=querierDB
```

##### Responder:
Create the required folders and untar the responder file.  Also copy over the executable files for flink-jdbc and standalone:
```sh
$ # Create the necessary folders
$ mkdir /opt/enquery/                      <- Parent folder
$ mkdir /opt/enquery/jobs/                 <- Parent folder for job execution configuration 
$ mkdir /opt/enquery/jobs/standalone/      <- Standalone Job Configuration folder
$ mkdir /opt/enquery/jobs/flink/           <- Flink Job configuration folder
$ mkdir /opt/enquery/native-libs/          <- C Native Libraries
$ mkdir /opt/enquery/app-libs/             <- Application Libraries (Standalone, Flink-JDBC, etc)
$ mkdir /opt/enquery/results/              <- Results folder
$ mkdir /var/EQResponder/inbox             <- Inbox for Data Schemas
$ # Copy over the native libraries
$ cp /home/encryptedquery/core/target/lib/native/libresponder.so /opt/enquery/native-libs/.
$ # Copy from the build location the responder tar file, untar it, then create a soft link. (Assuming you built the application off the home folder)
$ cp /home/encryptedquery/responder/dist/target/encryptedquery-responder-dist-derbydb-2.0.0.tar.gz /opt/enquery
$ cd /opt/enquery/
$ tar -xvf encryptedquery-responder-dist-derbydb-2.0.0.tar.gz
$ ln -s /opt/enquery/encryptedquery-responder-dist-2.0.0 responder
$ # Copy over the Application library files
$ cp /home/encryptedquery/standalone/app/target/encryptedquery-standalone-app-2.0.0.jar /opt/enquery/app-libs/.
$ cp /home/encryptedquery/flink/jdbc/target/encryptedquery-flink-jdbc-2.0.0.jar /opt/enquery/app-libs/.

$ # Copy over the 
```
**Note: If you are installing both the querier and responder on the same server add another folder for separation (/opt/enquery/responder-app/) and modify the softlink accordingly.
**Note: If you would like to use MariaDB to store execution information substitute the mariadb responder file (`encryptedquery-responder-dist-mariadb-2.0.0.tar.gz`) for the derby file

###### Responder Configuration:
Update the `/opt/enquery/responder/etc/encrypted.query.responder.business.cfg` and add/update the following to define the folder for the response files:
```
query.execution.results.path=/opt/enquery/results
```

If you are using MariaDB as your datastore also update the `/opt/enquery/responder/etc/org.ops4j.datasource-querier.cfg` file and add/update the following with the specifics of your installation(database server ip, database user/password, database name):
```
# Data Source connectivity
osgi.jdbc.driver.name = mariadb
dataSourceName=responder
url=jdbc:mariadb://192.168.200.74:3306/responder?characterEncoding=UTF-8&useServerPrepStmts=true
user=enquery
password=enquery
databaseName=responder
ops4j.preHook=responderDB
```

#### Setting up the Examples
Copy over the data schema and data source files from the examples folder to the responder.

``` sh
$ # Copy the Data Schemas
$ cp /home/encryptedquery/examples/xetra-jdbc/xetra-data-schema.xml /var/EQResponder/inbox/.
$ cp /home/encryptedquery/examples/standalone/phone-data-schema.xml /var/EQResponder/inbox/.
$ # Copy the Data Source Configuration files
$ cp /home/encryptedquery/examples/xetra-jdbc/org.enquery.encryptedquery.responder.flink.jdbc.runner.FlinkJdbcQueryRunner-MariaDBXETRA.cfg \      /opt/enquery/responder/etc/.
$ cp /home/encryptedquery/examples/standalone/org.enquery.encryptedquery.responder.standalone.runner.StandaloneQueryRunner-PhoneData-5K.cfg \     /opt/enquery/responder/etc/.
# Setup the sample data folder and copy the sample data
$ mkdir /opt/enquery/sampledata/
$ cp /home/encryptedquery/examples/standalone/phone-data.tar.gz /opt/enquery/sampledata/.
$ tar -xvf /opt/enquery/sampledata/phone-data.tar.gz
```

#### Starting the Applications

###### Querier
```sh
$ # Navigate to the querier folder
$ cd /opt/enquery/querier
$ # Start the Querier
$ bin/start
```
The querier karaf log file is located in `/opt/enquery/querier/data/log/karaf.log`

###### Responder
```sh
$ # Navigate to the responder folder
$ cd /opt/enquery/responder
$ # Start the responder
$ bin/start
```
The querier karaf log file is located in `/opt/enquery/responder/data/log/karaf.log`

** Note: It may take 1 to 2 minutes for the responder to injest the data schemas from the data schema inbox `/var/EQResponder/inbox`


[//]: # (These are reference links used in the body of this note and get stripped out when the markdown processor does its job. There is no need to format nicely because it shouldn't be seen. Thanks SO - http://stackoverflow.com/questions/4823468/store-comments-in-markdown-syntax)


   [Download]: https://github.com/En-Query/EncryptedQuery.git

   [PlBld]: <https://github.com/En-Query/EncryptedQuery/Building-README.md>
