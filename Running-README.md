# Running Encrypted Query

This document assumes that both Querier and Responder have been properly installed and started.  It may take a few minutes for the responder to injest the configured data schemas.

The basics of creating and runnning an Encrypted Query job are as follows:
* Create a Data-Source.cfg file which defines the data source.
* Create a Data-Schema.xml file which defines the fields in the Data source.
* Load these two files into the Responder to enable running a query against the Data Source.


### Create a DataSource Configuration File
Create a configuration file for the Data Source (This is the data you want to query against)  This configuration file tells the responder where the data is located, what format it is in, which method to use to execute the query and other performance parameters set based on the ability of the responder server.

Sample Flink JDBC Data Source Configuration file:
Lines starting with a # are comment lines.
```
# Configuration for a Flink-JDBC Runner on a MariaDB Business Articles table
name=Flink-JDBC-MariaDB-Business-Articles
description=Flink Engine running on a MariaDB Business Articles Database

# Name of the DataSchema describing the fields
data.schema.name=Business Articles

# Compute Threshold
.responder.computeThreshold=30000

# Flink tasks
.flink.parallelism=30

# Class name for the JDBC Driver to access the database
.jdbc.driver=org.mariadb.jdbc.Driver

# URL to connect to the database
.jdbc.url=jdbc:mariadb://192.168.200.74:3306/enquery?user=enquery&password=enquery

# SQL query that selects the data to be queried
.jdbc.query=SELECT id, complanyname, tickersymbol, articledate, articleURL, subject FROM businessarticles 

# Directory where Flink runtime is installed.
.flink.install.dir=/opt/flink

# Path to the flink-jdbc jar file.  This is the jar file implementing the query execution.
.jar.file.path=/opt/enquery/app-lib/encryptedquery-flink-jdbc-2.x.x.jar

# Path to a directory to use as the parent directory to store temporary files during the execution of the query.
.run.directory=/opt/enquery/jobs/flink
```
##### Data Source Configuration Parameters:
###### Common:
name=<Unique name for the Data source.>  This is value you will use to chose which data source to run a query against.
description=<General description of the data source>
data.schema.name=<Name of the Data schema associated with this data source> 
.run.directory=<Fully qualified path for a working folder to hold temp files>  set this to one of the `jobs` folders created
.compute.threshold=<How many records each processing thread reads before consolidating>

###### Standalone DataSources:
data.source.file=<Fully qualified name of the source file to query against.>
data.source.record.type=<How each record is defined>   Currently `json` is the only supported option.
.number.of.threads=<Number of processing threads to start to run the job>   Set to no more than the number of processor cores available.
.max.queue.size=<How many records in the input queue before pausing input for processing to catch up>  A throttling parameter 
.application.jar.path=/opt/enquery/app-lib/encryptedquery-standalone-app-2.x.x.jar  (File for Standalone Method)
.run.directory=/opt/enquery/jobs/standalone                                         
.java.options=-XX:+UseG1GC                                                          

Standalone data source file names need to be unique and start with `org.enquery.encryptedquery.responder.standalone.runner.StandaloneQueryRunner-` and end with `.cfg`

###### JDBC DataSources:
.column.encryption.partition.count=<Number of partitions to break data into.  Each partition will process data in a separate task>
.jdbc.driver=<jdbc database driver name>  org.mariadb.jdbc.driver
.jdbc.url=<Url to access the database> jdbc:mariadb://192.168.200.74:3306/enquery?user=enquery&password=enquery
.jdbc.query=<SQL statement to select data from the database> 
.flink.install.dir=<Fully qualified folder where Flink is installed>   /opt/flink
.jar.file.path=/opt/enquery/app-lib/encryptedquery-flink-jdbc-2.x.x-SNAPSHOT.jar  (File for Flink JDBC Method)
.run.directory=/opt/enquery/jobs/flink
.flink.parallelism=<Number of Flink tasks to use for processing>
.additional.flink.arguments=<Any additional parameters to pass to Flink>   (Optional)

JDBC data source file names need to be unique and start with `org.enquery.encryptedquery.responder.flink.jdbc.runner.FlinkJdbcQueryRunner-` and end with `.cfg`

###### Kafka Streaming DataSources:
.kafka.brokers=<URL of the kafka brokers.  If more than one, separate with a ",">
.kafka.topic=<Kafka Topic to consume data records from>
.flink.history.server.uri=<URI of Flink History Server to poll for status>  
.flink.install.dir=<Fully qualified folder where Flink is installed>   /opt/flink
.jar.file.path=/opt/enquery/app-lib/encryptedquery-flink-jdbc-2.x.x-SNAPSHOT.jar  (File for Flink JDBC Method)
.run.directory=/opt/enquery/jobs/flink
.flink.parallelism=<Number of Flink tasks to use for processing>
.additional.flink.arguments=<Any additional parameters to pass to Flink>   (Optional)

Kafka Streaming data source file names need to be unique and start with `org.enquery.encryptedquery.responder.flink.kafka.runner.FlinkKafkaQueryRunner-` and end with `.cfg`

Once you have created the data source configuration file it needs to be placed in the responder karaf configuration folder: `/opt/enquery/responder/etc/`

### Create a Data Schema xml File
The Data Schema xml file describes the data available in the data source.  Each Data Source configuration file has a parameter which references the data schema file.  An example data schema file looks like:
```
<?xml version="1.0" encoding="UTF-8" ?>
<dataSchema xmlns="http://enquery.net/encryptedquery/dataschema"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <name>Business Articles</name>
    <field>
        <name>id</name>
        <dataType>int</dataType>
        <isArray>false</isArray>
        <position>0</position>
    </field>
    <field>
        <name>companyname</name>
        <dataType>string</dataType>
        <isArray>false</isArray>
        <position>1</position>
    </field>
    <field>
        <name>tickersymbol</name>
        <dataType>string</dataType>
        <isArray>false</isArray>
        <position>2</position>
    </field>
    <field>
        <name>articledate</name>
        <dataType>string</dataType>
        <isArray>false</isArray>
        <position>3</position>
    </field>
    <field>
        <name>articleURL</name>
        <dataType>string</dataType>
        <isArray>false</isArray>
        <position>4</position>
    </field>
    <field>
        <name>subject</name>
        <dataType>string</dataType>
        <isArray>false</isArray>
        <position>5</position>
    </field>
</dataSchema>
```
Each field in the datasource must be described in the data schema.  The dataType, whether or not it is an array, and the position in the data source.

** Note: The position starts at position 0.
** Note: If the data source is a JDBC query, then the position element in the data schema must match the position in the select query. i.e the above data schema references a JDBC data source with the following query:
```
.jdbc.query=SELECT id, companyname, tickersymbol, articledate, articleURL, subject FROM businessarticles
```

After you create a data schema for the data source place it in the data schema inbox `/opt/enquery/dataschemas/inbox/`

The user interacts with Encrypted Query via REST interfaces or a Querier Web Interface.  A default UI has been created and can be accessed with the following URL:

	http://<querier.ip>:8182/querier
	
Substituting the querier server ip address for <querier.ip>

From here, you can perform the following steps:

1. Create query schema
2. Create query
3. Schedule the execution of a query
4. Download the result of a query execution
5. Decrypt the result of a query execution
Refer to the Examples README files for more info in running Encrypted Query
