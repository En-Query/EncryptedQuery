# Encrypted Query v2.1.4 - Release Notes

Encrypted query has been updated to make it more Enterprise ready.   The Querier and Responder have been seperated into Separate modules intended to run on Separate servers.   The system has been developed and testing on servers using Centos 7 OS.

#### Changes in release 2.1.4
* Updated Algorithm used for processing a query on the responder.   The new algorithm changes the processing of data in chunks (Compute Threshold) to procssing individual columns.   This improved performance on standalone processing by ~40%.  
  * Data Source configuration files can be updated to configure for the new algorithm.
  * .column.buffer.memory.mb=xxx  is used to configure how large the column array is allowed to grow to.   The higher the number the more memory required.
  * .algorithm.version=v2  is a new parameter in standalone configuration files.  The default is `v2` if the parameter is not given.

* Updated Field Types to more intuitivly handle arrays.   New field types are ( string, byte, byteArray, char, short, int, long, double, float, ip4, ip6, ISO8601Date )  If a field is an array of one of the prior field types just append `_list` to the end of the data type.
* When creating a Query Schema the Size is only applicable for String and ByteArray field types.  This parameter is optional but if a value is given that will be the max size for each element of that field to be processed. (i.e. if the Size is set to 20 only the first 20 characters of the field value are processed.  If no size is given then the full field value is processed.
* When creating a Query Schema the MaxArrayElement field is optional and only available for Array Field Types (those with a "_list" suffix).  If a value is not given then all array elements are processed.  If a value is given then only up to that number of array elements are processed.
* Null values are now properly handled in json records
* Selector values are always embedded in the response and therfore the reference in the UI has been removed.   In prior versions embedding the selector was optional.  
* When processing in a Flink environment (JDBC or Kafka Streaming) the .flink.parallelism parameter is how many parallel tasks will be used to run the job.  
* When processing using the GPU column processor in the Flink environment all Flink nodes must have an Nvidia GPU installed. 

#### Changes in release 2.1.3
* Added GPU processing support for Paillier encryption scheme.  Support is only available for Nvidia GPU's.   To enable GPU support will require an Nvidia video card, Nvidia drivers, and Nvidia CUDA 10.1+ installed on the server.  The software will need to be built on a platform that includes an Nvidia Card, Nvidia drivers, and Nvidia CUDA 10.1+ development tools.  Refer to the Build documentation  [Building-README.md][PlDb] to build with GPU support.
  
  - To enable GPU support edit the `org.enquery.encryptedquery.encryption.paillier.PaillierCryptoScheme.cfg` file.
  
   * On the responder side add/modify the property `paillier.column.processor=GPU` to enable GPU processing.
   * When processing with GPU support set the `paillier.gpu.libresponder.busy.policy` property to one of the following:
   ```
   CallerRuns       <-- If GPU is busy compute using the CPU.  (Default value)
   Wait             <-- If GPU is busy, wait until it frees up before continuing.
   GPUNow           <-- Running thread will immediately begin to use the GPU, oversubscribing GPUs if necessary 
   ```  
   * To enable GPU support on the querier for decryption set the `paillier.decrypt.response.method=GPU` property.    (Default is CPU)
#### Changes in release 2.1.2
* Fixed bug with ISO8601Date format.
* Modified Paillier Encryption to enhance decryption performance.
* change the default Paillier mod pow class to org.enquery.encryptedquery.encryption.impl.ModPowAbstractionGMPImpl to increase performance.
#### Changes in release 2.1.1
* Changed configuration file for setting storage location for queries and results.  On Querier the "blob-storage" parameter can now be found in: `/home/enquery/querier/etc/encrypted.query.querier.data.cfg` example file entries:
```
query.key.storage.root.url=file:///opt/enquery/query-key/      <-- Storage for Encryption keys
blob.storage.root.url=file:///opt/enquery/blob-storage/        <-- Storage for Encrypted query and result files
```
* Database tables updated to incorporate query start/stop times for result files and execution status.   If you are using MariaDB it is recommended to drop all existing tables and let them be re-installed when the querier and responder start up for the 1st time.   Using DerbyDB the databases are removed when you remove the old installation and will be re-created when 2.1.1 is started up.
* Flink Streaming job status updates through api calls to Flink.   Need to add .flink.history.server.uri=xxx to any flink streaming data source configuration files.   You can use the Flink Web dashboard IP:port or the history server uri for this.
* Fixed support for IPv4 and IPv6 field types.   They can now be set as a field type in the data schema.
* ISO8601 date format needs to be: `yyyy-mm-ddThh:mm:ss.sssZ`

#### New with release 2.1.0
* Encrypted Query has been redesigned to allow "drop-in" encryption.   The paillier encryption module is enabled by default.  Other encryption modules will follow.  Refer to Drop-In-Encryption-README.md for more information.
* Support for Querying of a Kafka stream.  Users select the total runtime of the query search and individual window time.  The responder will produce a response file for each window of the search.
* Data Chunk size limitation of 3 has been removed for DeRooij processing.  Limitations still exist for Yao implementations.
* Nested JSON fields are now supported.   Data schema fields may reference nested JSON Fields by using a '|' divider between element names.
Example:
    ```
    {
        "_score": null,
        "_source": {
            "layers": {
                "frame": {
                    "frame.interface_id": "0",
                    "frame.interface_id_tree": {
                        "frame.interface_name": "any"
                    }
                }
            }
        }
    }
    ```
    Field `_score` is referenced as "_score"
    Field `frame.interface_id` is referenced as "_source|layers|frame|frame.interface_id"

##### Release 2.0.1-SNAPSHOT
 * Changed dataPartitionSize query parameter to dataChunkSize.   This value is now in bytes instead of bits.  Default value 1.  This parameter and the paillier bit size will determine how many selector values
   can be searched for.  The formula is:
            "( paillierBitSize - 1 ) / ( dataChunkSize * 8)"   If Paillier bit size = 3072 and dataChunkSize = 1 (8 bits) then you can search for ( 3072 -1 ) / 8 = 383 selector values.  
        (Note: Using dataChunkSize > 3 will only work with ComputeEncryptedColumnBasic method.  It will fail using DeRooij or Yao implementations)
 * Updated license header in pom.xml files
 * general code cleanup
 * Fastest Responder performance is achieved when using ComputeEncryptedColumnDeRooijJNI method and using a dataChunkSize of 3.

Current Query methods are: 
 * Standalone <-- Used for processing against smaller flat files (less than 1Billion Records)
 * Flink JDBC <-- Using Apache Flink you can now query against JDBC databases.  Flink enables distributed processing allowing you to successfully query large database sets.  
 * Flink / Kafka Streaming <-- Using Apache Flink and Kafka you can now query against Kafka Streams.
 
### Recommended Hardware
By nature Quering an entire dataset and encrypting the results is a processor and memory intensive operation.  With that in mind, the following hardware is recommended:

 * Server with 32+ cores
 * 128GB Memory
 * Centos 7 OS

You can get away with testing on a smaller system:
 * 4 cores
 * 16GB memory

### Known Limitations
 * When encrypting a query use a hashBitSize of 18 or smaller.  The system will support a HashBitSize of 20, but that is not recommended
 * During testing setting the dataChunkSize to 10 with DeRooijJNI query showed the best performance.
 * When running on a small system (16GB memory) it is recommended to limit the number or processing threads to 4 or less.  (On a 128GB system you can safely use 60 threads)
 * Each additional thread will increase memory usage by ~500MB 
 * When setting up Flink cluster to run in a distributed processing environment it is recommended to create a NFS share that all servers can use and set that as the response output folder.  Set this share as the response folder on the responder server by adding the following to the `/opt/enquery/responder/etc/encrypted.query.responder.business.cfg`
  ```
query.execution.results.path=/opt/enquery/nfsshare-folder
  ```
* System has not been tested with versions of Java above 1.8
* The build process will sometimes fail.  Try repeating the build command.   Timing of integration tests and unit tests can be touchy.

[//]: # (These are reference links used in the body of this note and get stripped out when the markdown processor does its job. There is no need to format nicely because it shouldn't be seen. Thanks SO - http://stackoverflow.com/questions/4823468/store-comments-in-markdown-syntax)

   [PlDb]: <https://github.com/En-Query/EncryptedQuery/blob/master/doc/Building-README.md>
