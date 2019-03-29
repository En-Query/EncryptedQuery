# EncryptedQuery
## Deployment
Encrypted Query has been developed and tested on Centos 7 OS.  The application is written in Java & C and uses Apache Karaf as a launch platform.  Using REST interfaces users interact with the Querier to Configure, Generate, Schedule, & Decrypt a query.   Once Scheduled the Querier will submit the job to the Responder through REST calls to execute the query at the specified time.

Encrypted Query is separated into two pieces (Querier and Responder)  The Querier configures and Encrypts the query.   It is also used to Decrypt the result.   The Responder will run the query to generate a result file.
The system is designed for the Querier and Responder to run on separate servers.  For testing they can be configured to run on the same server.   Using Apache Flink users can also query against a JDBC database or a Kafka stream.   

#### Supported Datasets
* JSON flat file
* MariaDB/MySQL Databases
* Kafka Stream of JSON Records


### Building the Project

Refer to [Building-README.md][PlBld] for build Instructions

### Optional Software

Apache Flink (https://flink.apache.org/) for distributed processing.   Installation of Apache Flink is beyond the scope of this document.  Refer to the Apache Flink Quickstart (https://ci.apache.org/projects/flink/flink-docs-release-1.6/quickstart/setup_quickstart.html) for installation instructions.

MariaDB (https://mariadb.org/) for processing of JDBC data sources.   Installation of MariaDB is beyond the scope of this document.  Refer to the TechMint article for installing MariaDB on Centos 7 (https://www.tecmint.com/install-mariadb-in-centos-7/)

Apache Kafka (https://kafka.apache.org/) for Streaming.   Installation of Apache Kafka is beyond the scope of this document.  Refer to the Apache Kafka Quickstart (https://kafka.apache.org/quickstart/) 

### Setup
The querier and responder are designed to be run on seperate servers but can be configured to run on the same server for testing.  For the setup below it is assumed you downloaded or cloned the application into your `/home` folder.  If the application has been downloaded somewhere else use that location in the below commands.  
(Note: Native libraries are now contained in the encryption modules and no longer copied separatly)

##### Querier:
Create the base folders and install the querier file:
```sh
$ # Create the necessary folders
$ mkdir /opt/enquery/
$ # Copy the querier tar file, untar it and create a soft link
$ cp /home/encryptedquery/querier/dist/target/encryptedquery-querier-dist-derbydb-2.x.x.tar.gz /opt/enquery/.
$ cd /opt/enquery/
$ tar -xvf encryptedquery-querier-dist-derbydb-2.x.x.tar.gz
$ ln -s  encryptedquery-querier-dist-2.x.x querier
```
**Note: If you are installing both the querier and responder on the same server add another folder for separation (/opt/enquery/querier-app/) and modify the soft link accordingly.
**Note: If you would like to use MariaDB to store execution information substitute the mariadb querier file (`encryptedquery-querier-dist-mariadb-2.x.x.tar.gz`) for the derby file

###### Querier Configuration:
Update the `/opt/enquery/querier/etc/encrypted.query.querier.rest.cfg` file and add/update the responder info with your specifics:
```
context.path=/querier
responder.host=192.168.200.57
responder.port=8181
```
Update/add the `org.encryptedquery.querier.business.QueryCipher.cfg` file and set the hash bit size for the encryption:
```
hashBitSize=15
```

Update the `/opt/enquery/querier/etc/org.enquery.encryptedquery.encryption.paillier.PaillierCryptoScheme.cfg` file and add/update the following for the Paillier Crypto module:
```
paillier.prime.certainty=128
paillier.encrypt.query.task.count=2
paillier.modulusBitSize=3072
# paillier.encrypt.query.method.  Oneof ( Default, Fast, FastWithJNI )  Recommended FastWithJNI
paillier.encrypt.query.method = FastWithJNI
# paillier.column.processor is one of ( Basic, DeRooij, DeRooijJNI, Yao, YaoJNI ) Recommended DeRooijJNI
paillier.column.processor=DeRooijJNI
# Use only one of the below mod pow classes.  GMP has shown to be the faster.
#paillier.mod.pow.class.name=org.enquery.encryptedquery.encryption.impl.ModPowAbstractionJavaImpl
paillier.mod.pow.class.name=org.enquery.encryptedquery.encryption.impl.ModPowAbstractionGMPImpl

```

Update the `/opt/enquery/querier/etc/encrypted.query.querier.data.cfg` file and add/update the following to change the storage location of data generated:
```
query.key.storage.root.url=file:///opt/enquery/query-key/      
blob.storage.root.url=file:///opt/enquery/blob-storage/
```
Note: If you set the above locations, be sure to create the folders before running.  Default location for these files are: `/opt/enquery/querier/data/`

*Increasing Task Count will increase performance.   Recommended to set to 1/2 of the number of cores available on the server.
*query encryption methods are: (Default, Fast, and FastWithJNI) FastWithJNI uses c native libraries which speeds up encryption and is the recommended setting.

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
$ mkdir -p /opt/enquery/                      <- Parent folder
$ mkdir -p /opt/enquery/jobs/                 <- Parent folder for job execution configuration 
$ mkdir -p /opt/enquery/jobs/standalone/      <- Standalone Job Configuration folder
$ mkdir -p /opt/enquery/jobs/flink/           <- Flink Job configuration folder
$ mkdir -p /opt/enquery/app-libs/             <- Application Libraries (Standalone, Flink-JDBC, etc)
$ mkdir -p /opt/enquery/results/              <- Results folder
$ mkdir -p /opt/enquery/dataschemas/inbox     <- Inbox for Data Schemas
$ # Copy from the build location the responder tar file, untar it, then create a soft link. (Assuming you built the application off the home folder)
$ cp /home/encryptedquery/responder/dist/target/encryptedquery-responder-dist-derbydb-2.x.x.tar.gz /opt/enquery/.
$ cd /opt/enquery/
$ tar -xvf encryptedquery-responder-dist-derbydb-2.x.x.tar.gz
$ ln -s /opt/enquery/encryptedquery-responder-dist-2.x.x responder
$ # Copy over the Application library files
$ cp /home/encryptedquery/standalone/app/target/encryptedquery-standalone-app-2.x.x.jar /opt/enquery/app-libs/.
$ cp /home/encryptedquery/flink/jdbc/target/encryptedquery-flink-jdbc-2.x.x.jar /opt/enquery/app-libs/.
$ cp /home/encryptedquery/flink/kafka/target/encryptedquery-flink-kafka-2.x.x.jar /opt/enquery/app-libs/.
```
**Note: If you are installing both the querier and responder on the same server add another folder for separation (/opt/enquery/responder-app/) and modify the softlink accordingly.
**Note: If you would like to use MariaDB to store execution information substitute the mariadb responder file (`encryptedquery-responder-dist-mariadb-2.x.x.tar.gz`) for the derby file

###### Responder Configuration:
Update the `/opt/enquery/responder/etc/encrypted.query.responder.business.cfg` and add/update the following to define the folder for the response files:
```
query.execution.results.path=/opt/enquery/results
```
Update the `/opt/enquery/responder/etc/encrypted.query.responder.integration.cfg` file and add/update the following to define the folder for the data schemas:
```
inbox.dir=/opt/enquery/dataschemas/inbox
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
Configure the Paillier Encryption scheme with the same parameters as for the querier.

#### Setting up the Examples
Copy over the data schema and data source files from the examples folder to the responder.

``` sh
$ # Copy the Data Schemas
$ cp /home/encryptedquery/examples/responder-files/data-schemas/* /opt/enquery/dataschemas/inbox/.
$ # Copy the Data Source Configuration files
$ cp /home/encryptedquery/examples/responder-files/data-source-configurations/* /opt/enquery/responder/etc/.
# Setup the sample data folder and copy the sample data
$ mkdir /opt/enquery/sampledata/
$ cp /home/encryptedquery/examples/standalone/phone-data.tar.gz /opt/enquery/sampledata/.
$ tar -xvf /opt/enquery/sampledata/phone-data.tar.gz
$ cp /home/encryptedquery/examples/pcap-kafka/pcap-data.tar.gz /opt/enquery/sampledata/.
$ tar -xvf /opt/enquery/sampledata/pcap-data.tar.gz
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

** Note: It may take 1 to 2 minutes for the responder to injest the data schemas from the data schema inbox `/opt/enquery/dataschemas/inbox`


[//]: # (These are reference links used in the body of this note and get stripped out when the markdown processor does its job. There is no need to format nicely because it shouldn't be seen. Thanks SO - http://stackoverflow.com/questions/4823468/store-comments-in-markdown-syntax)


   [Download]: https://github.com/En-Query/EncryptedQuery.git

   [PlBld]: <https://github.com/En-Query/EncryptedQuery/Building-README.md>