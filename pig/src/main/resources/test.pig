/*
	 Test Pig Json  
	{"Duration": "00:20:56", "Date/Time": "07/03/2016 03:31 PM", "Caller #": "903-274-9597", "Callee #": "573-271-9802"}
	
	-param data_file=hdfs:///user/pirk/cdr5k.json
*/

REGISTER datafu-pig-1.4.0.jar;
REGISTER /opt/elephant-bird/json-simple-1.1.1.jar
REGISTER /opt/elephant-bird/elephant-bird-core-4.17.jar
REGISTER /opt/elephant-bird/elephant-bird-pig-4.17.jar
REGISTER /opt/elephant-bird/elephant-bird-hadoop-compat-4.17.jar
REGISTER encryptedquery-pig.jar;
REGISTER encryptedquery-core-1.0.0-SNAPSHOT.jar
REGISTER jnagmp-1.1.0.jar
REGISTER jna-4.0.0.jar

/* Define user defined functions */
DEFINE RowHash        org.enquery.encryptedquery.pig.udfs.RowHashUdf('$query_file_name', '$data_schema_dir');
DEFINE DataPartitions org.enquery.encryptedquery.pig.udfs.DataPartitionUdf('$query_file_name', '$data_schema_dir');
DEFINE EncryptColumn  org.enquery.encryptedquery.pig.udfs.ColumnEncryptionUdf('$query_file_name', '$data_schema_dir');
DEFINE ResponseStore  org.enquery.encryptedquery.pig.storage.XMLStorer('$query_file_name');

/* Raw Json data, each row contains a single field named 'json' of type Map */ 
rawData = LOAD '$input_file' USING com.twitter.elephantbird.pig.load.JsonLoader('-nestedLoad') AS (json:map[]);

/* Skip first header row */ 
filteredRawData = FILTER rawData BY json#'Date/Time' != '';

/* Convert to canonical represtation with fields matching (position, names, and types) the Data Schema */ 
canonicalData = FOREACH filteredRawData 
				GENERATE json#'Duration'  AS duration, 
					 json#'Date/Time' AS time,
					 /*ToDate(json#'Date/Time', 'MM/dd/yy hh:mm a') AS time,*/ 
					 json#'Caller #' AS caller, 
					 json#'Callee #' AS callee;

withRowHash = FOREACH canonicalData GENERATE RowHash(*) AS rowHash, *;

groupedByRowHash = GROUP withRowHash BY rowHash;

groupedPartitions = FOREACH groupedByRowHash 
					GENERATE group AS rowHash, DataPartitions(withRowHash) AS partitions;
 						
flattenedPartitions = FOREACH groupedPartitions
					  GENERATE rowHash, FLATTEN(partitions); 

groupedByColumn = FOREACH (GROUP flattenedPartitions BY partitions::col)
				  GENERATE group AS col,
				  		   flattenedPartitions.(rowHash, partitions::part);	

encryptedColumns = FOREACH groupedByColumn 
				   GENERATE col, EncryptColumn($1);

  
STORE encryptedColumns INTO '$output_file' USING  ResponseStore PARALLEL 1;
