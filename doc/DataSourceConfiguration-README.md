# Data source Configuration 

#### Common Configuration parameters
These are common configuration parameters required for all Data Source Configuration files.
 - name=(Unique Name for the data source)
 - description=(Description of the data source)
 - data.schema.name=(Data Schema to use for this Data Source)    `Multiple Data Sources can use the same Data Schema`
 - data.source.record.type=(Record format of the data)     `Currently JSON is the only supported option.   This parameter is not valid with Flink-JDBC processing` 
 - compute.threshold=(xxxxx)            `How many records to read before consolidating.  The higher the number, the more memory requried`
 - .run.directory=(temp folder to be used for processing)
 - .application.jar.path=(Fully qualified path to the jar file used for the processing method)
 	
#### Standalone Configuration parameters
 - .data.source.file=(Fully qualified name of data source file)
 - .number.of.threads=(How many processing threads to use to process the data file)   `For best performance set this to the number of cores on the server.`
 - .max.queue.size=(How many records can be stored on the processing queues at any given time.)  `This acts as a throttler to ensure the processing queues are not overwhelmed.`
 - .java.path=(Fully qualified path to the java executable)
 - .java.options=(Java Options to be sent when starting the standalone job)    `This parameter is optional` 

#### Hadoop MapReduce Configuration parameters
 - .hadoop.server.uri=(hdfs://serverIp:9000)    `Uri to access Hadoop`
 - .hadoop.username=(name of hadoop user to run as)   `Username which has access to hadoop to read/write data into hadoop with`
 - .hdfs.run.directory=(/user/enquery/phone-data/)    `Working folder within Hadoop to store temp files`
 - .hadoop.reduce.tasks=(Number of reduce tasks to use for processing)
 - .hadoop.mapreduce.map.memory.mb=(amount of memory to use for map processes in MB)  `Optional`
 - .hadoop.mapreduce.reduce.memory.mb=(amount of memory to use for reduce processes in MB)  `Optional`
 - .hadoop.mapreduce.map.java.opts=(Java options to run map job with)  `Optional`
 - .hadoop.mapreduce.reduce.java.opts=(Java options to run reduce job with)  `Optional`
 - .hadoop.install.dir=(home folder for hadoop) 
#### Flink JDBC Configuration parameters
