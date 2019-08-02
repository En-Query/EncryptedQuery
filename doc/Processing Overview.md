# Processing Overview

This is a behind the scenes look at how Encrpted Query works.   

The basic Encrypted Query process is:

 1) Configure and Start both the Querier and Responder Servers
 2) Creating a configuration file for the data source.
 3) Creating a Data Schema file to define the data within the data source.
 4) Create a Query Schema to define which field to query on and which fields from the Data Schema are to be processed.
 5) Create an encrypted query file with the data to search for.
 6) Run the encrypted query on the Responder Server to search the data source.
 7) Decrypt the response file from the query on the Querier Server

The Data Source and Data Schema are configured are done on the responder server.  The other steps are implemented on the querier server through a UI or REST interface calls. 

### Configuring the Data Source
In order to run Encrypted Query you need to have a source of data to search through.  This data can be from a flat file(Hadoop or local), JDBC Database, or a Streaming Kafka Topic.    The configuration file tells the responder how to search the data.
Which method (Hadoop MapReduce, Standalone, JDBC, or Kafka Stream) to use, Source Name, Data Format, etc.

A data source configuration file is created for each data source that will be used by the responder
##### Notes
 - Encrypted Query currently supports data in JSON format for non-JDBC data sources.
 - Some configuration options impact memory usage and processing on the server(s).
 - Clustered Processing (Flink or Hadoop) is out of scope for this document.
 - Some configuration options are common among all processing methods, some are specific to each.
 
### Defining the Data Schema
The next step is to define the data in the data source.  Details about the data itself (Field Names, Data Types, position, etc).  Each field in the data source should be defined in the Data Schema.   A Data Schema can be used for multiple data sources.   

### Defining the Query Schema
The end user selects which field from the Data Schema to query on as well as which fields they want to see in the result set.  The size or amount of data from each field can be limited. (i.e. if you have a Description field with 5000 characters but only want to see the 1st 100 characters).
###### Notes:
 - Each data field selected to be returned will need to be encrypted and processed so the amount of data should be kept to a minimum for efficiency.
 - The selector field must be one of the selected fields to return.
 - The data size to be returned is the combination of all the byte sizes of the fields seleted to be returned.

### Encrypting a Query
After selecting which Data Schema and Query Schema to use the next step is to Encrypt the Query.  Enter a list of data to search for and the Data Chunk Size.
###### Notes:
 - The Data Chunk Size can be set from 1 to 10 to help in efficiency of processing the data.
 - Setting the data chunk size to be a multiple of the average number of bytes being returned packs the data during processing thereby increasing efficiency.
 - Add 4 bytes to the data size being returned to account for embedding a selector hash.
 
  
 ```Example:  If the average number of bytes being returned is 52 the actual number of bytes to process will be 56 (Accounting for the 4 bytes of the selector hash).  Setting a chunk size of 8 would mean that each record could be processed in 7 chunks.  Setting the chunk size to 10 would require 6 chunks to process the same record.```
 - Setting Data Chunk Sizes to more than 3 is only applicable to the Paillier (DeRooij) Encryption scheme.
 
### Scheduling/Running the Query
Once the query has been encrypted it can now be run against a data source.   Select which data source to run against and input the Maximum number of hits per selector to allow.  This limits the number of hits if the data source is very large and you might encounter thousands of hits against a single entry.  

### Data Processing Methods
#### Standalone 
The standalone method is used for data sources which are flat files on the same server as the responder.   The data source configuration file can set how many threads to use when processing large files based on the amount of resources on the server.  
The basics of Standalone processing are:
 - A queue is created for each processing thread configured.
 - Data is read from the input file line by line.  It is assumed each line is a record to be processed.
 - Each record is then processed into a Hash value based on the selector and an array of bytes which represents the data selected by the query schema.
 - Each record is placed on a specific queue based on the hash value.
 - Each Processing thread reads records off its queue and loads them into memory.  After the compute threshold is reached the processing thread encrypts the data and reduces the data into a list of encrypted chunks.  
 - The processing thread then reads the next batch of records off the queue and continues this process until all records in the queue have been processed and the data has been reduced into a list of column id's and values.
 - Each processing thread then sends that list of column information to a response queue.
 - The response queue is then consolidated into a single response file to be returned to the querier for decryption.

###### Notes
 - Standalone application jar file is: encryptedquery-standalone-app-x.x.x.jar
 
#### Hadoop
The Hadoop method is used for data sources stored in Hadoop.  This method allows the processing of large data sets using Map Reduce and distributed processing over the hadoop nodes.  

The basics of Hadoop Map/Reduce processing method are:
 - A Mapper is started for each Block of the source data within the hadoop cluster.  
 - Each Mapper process will read the data line by line and assumes each line is a record to be processed.   
 - The record is then processed into a hash value based on the selector and an array of bytes which represents the data selected by the query schema.
 - The hash Value is the key which is used to sort the records as they are being sent to the Reducer.
 - Reduce tasks are started based on the number of reduce tasks from the data source configuration file.
 - The records from the mapper are sorted and distributed to the reduce tasks.
 - The reducers acts almost identically to the processing threads in standalone mode and will process the records in batches and reduce the data down to a list of column data which are written to temp files based on the column number.
 - A second phase of Map/Reduce processing is then started which combines the results from the 1st phase reduce process and outputs a single result file.
 - The result file is then written out to be returned to the querier for decryption.

###### Notes on Hadoop Processing:
 - The data source can be set to either a file or a folder within HDFS to process.  If set to a folder, all files within that folder are assumed to have the same formatting and are processed as a single set of data.
 - In version 1 of Encrypted Query Hadoop Processing was done in 3 phases.   Version 1 processing can be selected in the data source configuration. (.hadoop.processing.method=v1)
 - Hadoop application jar file is: encryptedquery-hadoop-mapreduce-app-x.x.x.jar
#### Flink JDBC

The Flink JDBC method is used for data sources where the data is stored in a Database.  This Method uses the distributed processing engine Apache Flink to process data at scale.

The basics of Flink JDBC processing method are:

 - A database connection is established through the JDBC driver identified in the data source
 - The SQL query from the data source is executed.
 - Each row returned by the database is processed into a hash value based on the selector field and an array of bytes which represents the data selected by the query schema.
 - The new records are grouped by the hash value and sent to a reduce group to be processed.  This is similar to the Processing thread in the standalone processing method.
 - The reducer outputs column information which is then ordered and sent to another reducer group to reduce the data to a single value for each column.
 - Once the columns have been reduced the output is written to a response file to be returned to the querier for decryption.
 
 ###### Nodes on Flink JDBC processing
  - The open source MariaDB JDBC driver is the default JDBC driver delivered with Encrypted Query.  Other JDBC drivers can be used by setting the JDBC driver parameter in the data source configuration and also including the `jar` file in the flink/lib folder on each node of the Flink cluster.
  - Date fields should be output in the format "%Y-%m-%dT%TZ" for an ISO8601 date format.
  - Flink JDBC application jar file is: encryptedquery-flink-JDBC-app-x.x.x.jar
  
#### Flink Kafka
The Flink Kafka method is used for streaming data sources.  The Kafka stream is searched for a period of time (minutes, hrs, days, etc) or indefinitly.  Within that search time data is processed in individual time windows.   `Example: runTimeSeconds = 86400 (seconds in 24hrs or 1 day) windowLengthSeconds = 3600 (1hr time window) would return 24 responses for the run or 1 response for each time window.`  This method uses the distributed processing engine Apache flink to process data at scale.

The basics of Flink Kafka processing method are:

 - User selects the amount of time to search the data stream and window time length
 - Flink then captures data from the offset point selected by the user.  (`fromEarliest` selects all current data for that kafka topic, `fromLatest` selects all new data from the kafka topic after the query starts.  `fromLatestCommit` selects all new data since the last time this query was executed. )
 - Flink will read and process data for each time window and create separate response files for each window.
 - Each record read by Flink is processed in the same manner as the processing methods above.  (Record created from a hash value and byte array)
 - Records are then sorted and reduced by the hash value into colum ids and values.
 - Colum values are reduced into a single value for each column.
 - Response files are created for each time window to be used by the querier for decryption.
 
 ###### Notes on Flink Kafka processing
 - Some windows will not return a response value.  Only those windows that processed data will have a response file.
 - Search time and window time are entered in seconds.
 - Max Hits per selector is per window not the entire job.
 - Flink JDBC application jar file is: encryptedquery-flink-Kafka-app-x.x.x.jar
 



