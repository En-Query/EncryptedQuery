# Encrypted Query v2.1.0 - Release Notes

Encrypted query has been updated to make it more Enterprise ready.   The Querier and Responder have been seperated into Separate modules intended to run on Separate servers.   The system has been developed and testing on servers using Centos 7 OS.

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
  * When using Derby database you will see a lot of these messages in the karaf.log file on both the querier and responder
  ```
2018-11-21T13:48:51,854 | WARN  | qtp877807613-295 | SqlExceptionHelper               | 156 - org.hibernate.core - 5.2.9.Final | 01J01 : [0] derby-data/responder
2018-11-21T13:48:51,860 | WARN  | qtp877807613-295 | SqlExceptionHelper               | 156 - org.hibernate.core - 5.2.9.Final | SQL Warning Code: 10000, SQLState: 01J01
```
* ip4 and ip6 datatypes have been causing some errors.  Set these fields to string datatypes and use a variable length 15 for ip4 and 30 for ip6
* System has not been tested with versions of Java above 1.8
* The build process will sometimes fail.  Try building a second time.  If it builds past the paillier Encryption build 'with-precompiled-native-libs'
