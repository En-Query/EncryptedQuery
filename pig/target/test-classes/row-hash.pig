DEFINE RowHash        org.enquery.encryptedquery.pig.udfs.RowHashUdf('$query_file_name', '$config_file_name');
DEFINE DataPartitions org.enquery.encryptedquery.pig.udfs.DataPartitionUdf('$query_file_name', '$config_file_name');
DEFINE EncryptColumn  org.enquery.encryptedquery.pig.udfs.ColumnEncryptionUdf('$query_file_name', '$config_file_name');
DEFINE ResponseStore  org.enquery.encryptedquery.pig.storage.XMLStorer('$query_file_name');

data = LOAD '$input' AS (id:INT, title:CHARARRAY, author:CHARARRAY, price:DOUBLE, qty:INT);

withRowHash = FOREACH data GENERATE RowHash(*) AS rowHash, *;
DUMP withRowHash;
DESCRIBE withRowHash;

groupedByRowHash = GROUP withRowHash BY rowHash;
DUMP groupedByRowHash;
DESCRIBE groupedByRowHash;

groupedPartitions = FOREACH groupedByRowHash GENERATE group AS rowHash, DataPartitions(withRowHash) AS partitions;
 					
 						
DUMP groupedPartitions;
DESCRIBE groupedPartitions;

flattenedPartitions = FOREACH groupedPartitions
					  GENERATE rowHash, FLATTEN(partitions); 

DUMP flattenedPartitions;
DESCRIBE flattenedPartitions;

groupedByColumn = FOREACH (GROUP flattenedPartitions BY partitions::col)
				  GENERATE group AS col,
				  		   flattenedPartitions.(rowHash, partitions::part);	

DUMP groupedByColumn;
DESCRIBE groupedByColumn;

encryptedColumns = FOREACH groupedByColumn GENERATE col, EncryptColumn($1);

DUMP encryptedColumns;
DESCRIBE encryptedColumns;
	  
STORE encryptedColumns INTO '$output' USING  ResponseStore PARALLEL 1;
