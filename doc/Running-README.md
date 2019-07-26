# Running Encrypted Query

This document assumes that both Querier and Responder have been properly installed and started.  It may take a few minutes 
for the responder to ingest the configured data schemas.

The basics of creating and runnning an Encrypted Query job are as follows:
* Create a Data-Source.cfg file which defines the data source.
* Create a Data-Schema.xml file which defines the fields in the Data source.
* Load these two files into the Responder to enable running a query against the Data Source.


### Create a DataSource Configuration File
Create a configuration file for the Data Source (This is the data you want to query against.)
This configuration file tells the responder where the data is located, what format it is in, which method to use to execute the 
query and other performance parameters set based on the ability of the responder server.

Sample Flink JDBC Data Source Configuration file(lines starting with a # are comment lines):
```
# Configuration for a Flink-JDBC Runner on a MariaDB Business Articles table
name=Flink-JDBC-MariaDB-Business-Articles
description=Flink Engine running on a MariaDB Business Articles Database

# Name of the DataSchema describing the fields
data.schema.name=Business Articles

# Column Buffer Memory MB
.column.buffer.memory.mb=2048

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
This version includes the following out-of-the-box data sources:

|Runtime Engine|Input Data Format|Description|
|----------|-----------|
|Standalone Java|JSON|Single node query execution on a file where each line is a JSON object.|
|Apache Flink|JDBC|Flink execution on a JDBC data source. Supports Yarn, and Flink cluster mode.|
|Apache Flink|Stream of JSON objects.|Flink execution on a Kafka stream of JSON objects. Supports Yarn, and Flink cluster mode.|

###### Common
|Name|Description|
|----------|-----------|
|name|Unique name for the Data source. This is value you will use to chose which data source to run a query against.|
|description|General description of the data source|
|data.schema.name|Name of the Data schema associated with this data source.|
|.application.jar.path|Fully qualified path to the jar file of the application implementing the query execution.|
|.run.directory|Fully qualified path for a working folder for the application, to hold temp files. Set this to one of the `jobs` folders created.|

###### Standalone DataSource
This option is suitable for single node executions. It does not require any third party platform, just Java.
Standalone data source file names need to be unique and start with
`org.enquery.encryptedquery.responder.standalone.runner.StandaloneQueryRunner-` and end with `.cfg`. For example:
`org.enquery.encryptedquery.responder.standalone.runner.StandaloneQueryRunner-Books.cfg`.


|Name      |Description|
|----------|-----------|
|data.source.file|Fully qualified path to the source file to query against.|
|data.source.record.type|How each record is defined. Currently `json` is the only supported option.|
|.algorithm.version|Currently, there are 2 versions of the standalone algorithm. Possible values 'v1' and 'v2'. Select 'v2' for better performance.|
|.number.of.threads|Number of processing threads. Set to no more than the number of processor cores available.|
|.application.jar.path|Fully qualified path to the standalone jar file.|
|.run.directory|Directory to use as the current working directory for the standalone process. Also used to temporary store query and configuration files.|
|.java.options|Additional JVM options, such as heap size, etc. For example: -XX:+UseG1GC|
|.max.queue.size|Only used for algorithm 'v1'. Size of intermediate queues used to pass data among the processing threads. A throttling parameter. |
|.compute.threshold|Only used for algorithm 'v1'. How many records each processing thread reads before consolidating.|
|.column.buffer.memory.mb|Only used for algorithm 'v2'. Maximum memory to reserve for the in-memory buffer holding data chunks from parsed records. Increasing this may improve performance. Optional. |
|.max.record.queue.size|Only used for algorithm 'v2'. Size of internal queue used to store parsed data records. Optional. |
|.max.column.queue.size|Only used for algorithm 'v2'. Size of internal queue used to store tasks for worker threads. Optional. |
|.max.response.queue.size|Only used for algorithm 'v2'. Size of internal queue used to store response data going to output file. Optional. |
|.java.path|Path to the java executable program. e.g. /usr/bin/java|


###### Apache Flink against JDBC DataSources
This option allows you to run an encrypted query against a JDBC data source on a Flink cluster.  The JDBC driver jar needs to be installed in the Flink lib directory. Data source file names need to be unique and start with `org.enquery.encryptedquery.responder.flink.jdbc.runner.FlinkJdbcQueryRunner-` and end with `.cfg`


|Name      |Description|
|----------|-----------|
|.jdbc.driver|JDBC database driver name, for example: org.mariadb.jdbc.driver|
|.jdbc.url|Url to access the database. For example: jdbc:mariadb://192.168.200.74:3306/enquery?user=XXX&password=XXX|
|.jdbc.query|SQL statement to select data from the database.|
|.flink.install.dir|Fully qualified path to the Flink installation. For example:  /opt/flink|
|.jar.file.path|Path to the Flink JDBC application jar. For example: /opt/enquery/app-lib/encryptedquery-flink-jdbc-2.1.4.jar |
|.run.directory|Directory to use as the current working directory for the process. Also used to temporary store query and configuration files.|
|.flink.parallelism|Number of Flink tasks to use for processing. Optional.|
|.column.buffer.memory.mb|Maximum memory to reserve for the in-memory buffer holding data chunks from parsed records. Increasing this may improve performance. Optional. Default is 100M.|
|.additional.flink.arguments|Any additional parameters to pass to Flink. Optional.|


###### Apache Flink against stream of Kafka JSON objects
This option allows you to run an encrypted query against a Kafka stream of JSON objects on a Flink cluster.  
Data source file names need to be unique and start with
`org.enquery.encryptedquery.responder.flink.kafka.runner.FlinkKafkaQueryRunner-` and end with `.cfg`.
Once you have created the data source configuration file it needs to be placed in the responder karaf
configuration folder: `/opt/enquery/responder/etc/`

|Name      |Description|
|----------|-----------|
|.kafka.brokers|URL of the kafka brokers.  If more than one, separate with a ","|
|.kafka.topic|Kafka Topic to consume data records from.|
|.flink.history.server.uri|URI of Flink History Server to poll for status.| 
|.flink.install.dir|Fully qualified folder where Flink is installed. Example: /opt/flink|
|.run.directory|Directory to use as the current working directory for the process. Also used to temporary store query and configuration files.|
|.flink.parallelism|Number of Flink tasks to use for processing. Optional.|
|.additional.flink.arguments|Any additional parameters to pass to Flink. Optional.|
|.column.buffer.memory.mb|Amount of memory in MB to be allocated for Column processing. . Default is 100M.|

### Create a Data Schema xml File
The Data Schema xml file describes the data available in the data source. 
You can find the XML Schema for this file in `xml/src/main/resources/org/enquery/encryptedquery/xml/schema/data-schema.xsd`. 
Valid data types are:

* `byte`
* `byte_list`
* `short`
* `short_list`
* `int`
* `int_list`
* `long`
* `long_list`
* `float`
* `float_list`
* `double`
* `double_list`
* `char`
* `char_list`
* `string`
* `string_list`
* `byteArray`
* `byteArray_list`
* `ip4`
* `ip4_list`
* `ip6`
* `ip6_list`
* `ISO8601Date`
* `ISO8601Date_list`


Each Data Source configuration file has a parameter which references the data schema file.
An example data schema file looks like:


```xml
<?xml version="1.0" encoding="UTF-8" ?>
<dataSchema xmlns="http://enquery.net/encryptedquery/dataschema"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <name>Business Articles</name>
    <field>
        <name>id</name>
        <dataType>int</dataType>
        <position>0</position>
    </field>
    <field>
        <name>companyname</name>
        <dataType>string</dataType>
        <position>1</position>
    </field>
    <field>
        <name>tickersymbol</name>
        <dataType>string</dataType>
        <position>2</position>
    </field>
    <field>
        <name>articledate</name>
        <dataType>string</dataType>
        <position>3</position>
    </field>
    <field>
        <name>articleURL</name>
        <dataType>string</dataType>
        <position>4</position>
    </field>
    <field>
        <name>subject</name>
        <dataType>string</dataType>
        <position>5</position>
    </field>
</dataSchema>
```
Each field in the datasource must be described in the data schema.  
The dataType, and the position in the data source.  The position is used for data 
formats that do not support access fields by name, for example CSV files, etc.

Note: The position starts at position 0.
Note: If the data source is a JDBC query, then the position element in the data schema must match the position in the select query. i.e the above data schema references a JDBC data source with the following query:

	.jdbc.query\=SELECT id, companyname, tickersymbol, articledate, articleURL, subject FROM businessarticles

After you create a data schema for the data source, place it in the data schema inbox directory in the Responder server: `/opt/enquery/dataschemas/inbox/`

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
