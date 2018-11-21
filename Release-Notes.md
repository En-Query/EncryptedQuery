# Encrypted Query v2.0.0 - Release Notes

Encrypted query has been updated to make it more Enterprise ready.   The Querier and Responder have been seperated into Separate modules intended to run on Separate servers.   The system has been developed and testing on servers using Centos 7 OS.

Current Query methods are: 
 * Standalone <-- Used for processing against smaller flat files (less than 1Billion Records)
 * Flink JDBC <-- Using Apache Flink you can now query against JDBC databases.  Flink enables distributed processing allowing you to successfully query large database sets.  
 
### Recommended Hardware
By nature Quering an entire dataset and encrypting the results is a processor and memory intensive operation.  With that in mind, the following hardware is recommended:

 * Server with 32+ cores
 * 128GB Memory
 * Centos 7 OS

You can get away with testing on a smaller system:
 * 4 cores
 * 16GB memory

### Known Limitations
 * It is recommended on the first build from the parent folder to build with the native libs (mvn clean install -P native-libs)
 * When encrypting a query use a hashBitSize of 18 or smaller.  The system will support a HashBitSize of 20, but that is not recommended
 * dataPartitionSize should be set to 24.   It must be in increments of 8 and currently only supports (8, 16, 24, 32)
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

* The User Interface given is very basic and limited in functionallity.

