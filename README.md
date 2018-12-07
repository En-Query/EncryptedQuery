# EncryptedQuery

[![N|Solid](https://enquery.net/wp-content/uploads/2018/03/EnQuery-logo-400x100.jpg)](https://enquery.net) v2.0.1-SNAPSHOT

## Overview

Encrypted Query is designed to allow a user to query a remote database without revealing the contents of the query or the results to the database server.  This is accomplished using techniques from Private Information Retrieval (PIR) with Paillier encryption. Encrypted Query has two distinct sides - the Querier and the Responder.  The Querier side prepares encrypted queries which are then submitted to the Responder side.  The Responder side accepts encrypted queries and executes them on some data source without being able to determine the criteria contained in the query, or the data that is returned. All records of the queried data source are always scanned during query execution. 


### New Features!

  - Separated the Querier and Responder into standalone Entities.  Allows you to separate the Encrypting/Decrypting of a query in an Enclave separating it from the Query Execution.   The Responder will be located where the data is and using REST interfaces to communicate between the Querier and Responder.
  - Basic UI to communicate with the Querier
  - Query files are in xml format
  - Uses Apache Karaf as a container for REST interfaces and operations
  - Stores execution Information in a self contained database (Derby) or in a user established MariaDB.
  - Run the query in an Apache Flink cluster for distributed processing (JDBC queries now, others to follow)
  
 
 

### Additional Documentation

| Plugin | README |
| ------ | ------ |
| Deployment | [Deployment-README.md][PlDb] |
| Building | [Building-README.md][PlGh] |
| Examples | [Examples-README.md][PlGd] |



## Export Control

This distribution includes cryptographic software. The country in which you currently reside may have restrictions on the import, possession, use, and/or re-export to another country, of encryption software. BEFORE using any encryption software, please check your country's laws, regulations and policies concerning the import, possession, or use, and re-export of encryption software, to see if this is permitted. See [The Wassenaar Arrangement](http://www.wassenaar.org) for more information. 
The U.S. Government Department of Commerce, Bureau of Industry and Security (BIS), has classified this software as Export Commodity Control Number (ECCN) 5D002.C.1, which includes information security software using or performing cryptographic functions with asymmetric algorithms. The form and manner of this Apache Software Foundation distribution makes it eligible for export under the License Exception ENC Technology Software Unrestricted (TSU) exception (see the BIS Export Administration Regulations, Section 740.13) for both object code and source code.
The following provides more details on the included cryptographic software:
Encrypted Query implements cryptographic software and is designed for use with the Java Cryptography Architecture (JCA).
See [Oracle Product ECCN Matrices](http://www.oracle.com/us/products/export/eccn-matrix-345817.html)

## License
EncryptedQuery is an open source project allowing user to query databases with queries under homomorphic encryption to securing the query and results set from database owner inspection. 
Copyright (C) 2018  EnQuery LLC 

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.



[//]: # (These are reference links used in the body of this note and get stripped out when the markdown processor does its job. There is no need to format nicely because it shouldn't be seen. Thanks SO - http://stackoverflow.com/questions/4823468/store-comments-in-markdown-syntax)

   [PlDb]: <https://github.com/En-Query/EncryptedQuery/Building-README.md>
   [PlGh]: <https://github.com/En-Query/EncryptedQuery/Deployment-README.md>
   [PlGd]: <https://github.com/En-Query/EncryptedQuery/Examples-README.md>
   [PlLs]: <https://github.com/En-Query/EncryptedQuery/LICENSE>
