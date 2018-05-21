# Encrypted Query

## Overview

Encrypted Query is designed to allow a user to query a third party database without revealing the contents of the query search, or the results of that query. To hide the contents of the query Encrypted Query utilizes homomorphic encryption technologies, specifically, pallier encryption, which allows for this unique security of the query.


Encrypted Query has two distinct sides - the Querier and Responder.



## Building the Project

Encrypted Query is a Java project with a Maven build system. The pom.xml resides at the root project level.

To build:

    Navigate to the root of the project
    Execute the command -   mvn clean install -P native-libs


## Export Control

This distribution includes cryptographic software. The country in which you currently reside may have restrictions on the import, possession, use, and/or re-export to another country, of encryption software. BEFORE using any encryption software, please check your country's laws, regulations and policies concerning the import, possession, or use, and re-export of encryption software, to see if this is permitted. See http://www.wassenaar.org/ for more information.

The U.S. Government Department of Commerce, Bureau of Industry and Security (BIS), has classified this software as Export Commodity Control Number (ECCN) 5D002.C.1, which includes information security software using or performing cryptographic functions with asymmetric algorithms. The form and manner of this Apache Software Foundation distribution makes it eligible for export under the License Exception ENC Technology Software Unrestricted (TSU) exception (see the BIS Export Administration Regulations, Section 740.13) for both object code and source code.

The following provides more details on the included cryptographic software:

Encrypted Query implements cryptographic software and is designed for use with the Java Cryptography Architecture (JCA).

See http://www.oracle.com/us/products/export/eccn-matrix-345817.html

## License

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

    (http://www.apache.org/licenses/LICENSE-2.0)

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License. See accompanying LICENSE file.



