# Running Encrypted Query

This document assumes that both Querier and Responder have been properly installed and started.  It may take a few minutes for the responder to injest the configured data schemas.  

The user interacts with Encrypted Query via REST interfaces or a Querier Web Interface.  A default UI has been created and can be accessed with the following URL:

	http://<querier.ip>:8182/querier
	
Substituting the querier server ip address for <querier.ip>

From here, you can perform the following steps:

1. Create query schema
2. Create query
3. Schedule the execution of a query
4. Download the result of a query execution
5. Decrypt the result of a query execution

The basics of Creating and Running Encrypted Query are as follows:

### Create a DataSource Configuration File
Create a configuration file for the Data Source (This is the data you want to query against)  This configuration file tells the responder where the data is located, what format it is in, and other parameters to execute the query based on the ability of the responder server.

Sample Data Source Configuration file.  The 1st line is a general description of the data source, the rest of the lines are self explanitory:
```
# Configuration for a Flink-JDBC Runner on a MariaDB Business Articles table
name=Flink-JDBC-MariaDB-Business-Articles
description=Flink Engine running on a MariaDB Business Articles Database
type=Batch

# Name of the DataSchema describing the fields
data.schema.name=Business Articles

# Class name of the Encryption Method
.column.encryption.class.name=org.enquery.encryptedquery.responder.wideskies.common.ComputeEncryptedColumnDeRooijJNI

# Class name of the ???
.mod.pow.class.name=org.enquery.encryptedquery.encryption.impl.ModPowAbstractionJavaImpl

# JNI Library File
.jni.library.path=/opt/enquery/native-libs/libresponder.so

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
.jar.file.path=/opt/enquery/app-lib/encryptedquery-flink-jdbc-1.0.0-SNAPSHOT.jar

# Path to a directory to use as the parent directory to store temporary files during the execution of the query.
.run.directory=/opt/enquery/jobs/flink
```

If the Data Source is for a flat file to be queried using the Standalone method then remove `.jdbc`  and `.flink` entries, change the .jar.file.path to `/opt/enquery/app-lib/encryptedquery-standalone-app-2.0.0.jar` and the run folder to `/opt/enquery/jobs/standalone` and add the following parameters:
```
# Location of Data File
data.source.file=/opt/enquery/sampledata/cdr10M.json
# Data File Record Type
data.source.record.type=json
# Number of threads
.number.of.threads=60
# Max Queue Size
.max.queue.size=100000
# Compute Threshold
.compute.threshold=30000
```

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

After you create a data schema for the data source place it in the data schema inbox `/var/EQResponder/inbox/`

Refer to the Examples README files for more info in running Encrypted Query
