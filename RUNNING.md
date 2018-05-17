# Running Encrypted Query


There are three steps to running Encrypted Query:
1. Create an encrypted query (on the querier side)
2. Run the Responder to query against the data (on the server side)
3. Decrypt the server response (on the querier side)

Currently Encrypted query supports querying against data in JSON format.  The Encrypted Query Responder (Server side piece that actually queries the data) supports three different modes: standalone, kafka streaming and Hadoop map/reduce.  Steps 1 and 3 do not depend on the mode.


## Pre-requisites
* To build Encrypted Query will require Oracle Java 1.8x JDK and Apache Maven.  It will also require a gcc compiler.

* To build download the project from github and build with the command: `mvn clean install -P native-libs`

This will create a distribution tar file (encryptedquery-1.0.0-SNAPSHOT-dist.tar.gz) in the target folder.  Un-tar this file into a working folder.
 
* To run Encrypted Query you will need to have java 1.8x runtime installed on the server.

* If you plan on running in streaming mode you will also need a kafka server running.

* To run in Hadoop map/reduce mode will require a Hadoop installation with map/reduce.


Installing of these pre-requisites  are beyond the scope of this document but information can be found:

**Java JDK**:  `http://www.oracle.com/technetwork/java//jdk8-downloads-2133151.html`

**Apache kafka**: `https://kafka.apache.org/downloads`

**Hadoop**: `http://hadoop.apache.org/`

## Required Files

Below we describe how to perform a test runs in each mode.  The following files are required to execute an Encrypted Query:

* *Data Schema file* - This file describes the data to be queried against.  Each field in the JSON record is described.

* *Query Schema file* - This file identifies the query name, data schema file, and which fields from the data schema are to be retrieved in the result as well as the field to query against(Selector field).

* *Selector data file* - This file contains a unique id for the query as well as specifying the specific selectors (search terms) we are interested in.

* *Data file* - This is the actual data we will be quering against

### Examples of each file:

**Data Schema file** (`dataschema.xml`):

```
<?xml version="1.0" encoding="UTF-8" ?>
<schema xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" >
    <schemaName>Simple Data</schemaName>
    <element>
        <name>name</name>
        <type>string</type>
        <isArray/>
    </element>
    <element>
        <name>age</name>
        <type>int</type>
        <isArray>false</isArray>
    </element>
    <element>
        <name>children</name>
        <type>string</type>
        <isArray>true</isArray>
    </element>
</schema>
```

**Query Schema file** (`queryschema.xml`):

```
<?xml version="1.0" encoding="UTF-8" ?>
<schema xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <schemaName>simple query</schemaName>
    <dataSchemaName>Simple Data</dataSchemaName>
    <selectorName>name</selectorName>
    <elements>
        <name>name</name>
        <name>age</name>
    </elements>
</schema>
```

**Selector file** (`query.txt`):

```
4c0291a8-29e2-11e7-93ae-92361f002671
Bob
```
The first line is a UUID value that represents the id of the query.  Our value was generated using the online UUID generator https://www.uuidgenerator.net/.  The subsequent lines contain the term(s) we are searching for, which will be matched to the field indicated by the selectorName tag in the queryschema.xml file shown above.
  

**Data file** `datafile.json` contains the data that the server will use to process our query, with one JSON record per line:

```
{ "name": "Alice", "age": "31", "children": [ "Zack", "Yvette" ] }
{ "name": "Bob",   "age": "25", "children": [ "Xavier", "Wendy" ] }
{ "name": "Chris", "age": "43", "children": [ "Donna" ] }
{ "name": "Donna", "age": "19", "children": [ ] }
```

## Example run in Standalone mode
In the Simple Query example our test query is looking for records with name = "Bob" and is asking for both the name and the age fields for such records.  The simple example files are available and can be executed from the *examples/simple/* folder.

To run the simple query enter the command: `./run_simple_query.sh`

This script will execute the *Generate Query* script which builds the query.  When this is finished two files will be created:  

*demographic-query*  - This is the encrypted query file to be used by the responder to query the data
*demographic-querier* - This is the key file to be used to decrypt the result file from the responder

It then executes the *Responder* script which will execute the query against the data file in standalone mode.  This will create the result file:

*demographic-query-result*  - This file is the encrypted result file.

Finally the script will execute the *Decrypt* script which will take the result file and convert it into plain text.  This is output to the file:

*demographic-plain-result*  - Plain Text of the results from the query.
 
Details of each step is shown below.

### Creating the encrypted query file:
Creating an encrypted query is done on the querier side.  The first steps are to prepare the data schema, query schema, and selector files. 
For this run we will use the files `dataschema.xml`, `queryschema.xml`, and `query.txt` as above.

Commands to create the encrypted query (_1st line sets the ENQUERY HOME folder location_):

```
ENQUERY_HOME=/folder of the expanded distribution file/
java -Djava.library.path=$ENQUERY_HOME/lib/native/ -cp $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.querier.wideskies.QuerierDriver -a encrypt -c 128 -dps 8 -hb 12 -pbs 3072  -i query.txt -qt "simple query" -nt 1 -qs queryschema.xml -ds dataschema.xml -m default -o demographic
```
This should produce two new files, a *query file* `demographic-query` and a *querier file* `demographic-querier`.  The query file contains the encrypted query to be used by the server to perform our private query.  The querier file contains sensitive private key information needed to decrypt the server response, and so should not be sent to the server.

Some important options:

```
| Option | Description |
| ------ | --------------------- |
| -a     | Action to perform, (Options encrypt, decrypt) in this case 'encrypt' to create an encrypted query. |
| -c     | Certainty of prime generation for Paillier |
| -dps   | Partition bit size in data partitioning must be a multiple of 8 |
| -hb    | Bit size of keyed hash |
| -pbs   | Paillier modulus size N |
| -i     | The selector file name. | 
| -qt    | The schema name of the query.  This value needs to match the schemaName value in the query schema file defined earlier. | 
| -nt    | Number of threads. | 
| -qs    | query schema file name. | 
| -ds    | data schema file name. | 
| -o     | Prefix of output files names. | 
| -m     | Query generation method.  (Options: default, fast, fastwithjni) |

**Notes:** Increasing the hash bit size (-hb) will increase the query file size while decreasing the result file size.
          It is not recommended to go beyond 22 for the hash bit size.   Recommended settings are between 15 and 18.   
          If extra cores are available, increasing the number of threads will increase performance (e.g. `-nt 4`).
          
          Partition bit size (-dps) must be a multiple of 8 and should not exceed 32.  It is recommended to use 16 or 24.

          The user can specify one of three query generation methods with the -m option (or pir.encryptQueryMethod configuration property).
          If the value is not specified, "default" is assumed.
 
          The "fast" option uses additional optimizations (shortened Paillier exponents and precomputed tables) to 
          speed up the query generation process.  This method starts being faster than the default method for
          larger values of the hash bit size (-hb).

          The "fastwithjni" query generation method uses the same optimizations as in the "fast" method, 
          but uses the native library libquerygen.so to achieve several times the speed.
          
```

### Running the Responder (standalone mode)
To perform the query, we copy the encrypted query file, data schema, & query schema files to the server side and then run the commands:

```
ENQUERY_HOME=/folder of the expanded distribution file/
java -Djava.library.path=$ENQUERY_HOME/lib/native/ -cp $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.responder.wideskies.ResponderDriver \
  -d base -ds dataschema.xml -i datafile.json -p standalone -qs queryschema.xml -q demographic-query -o demographic-query-result
```
This will produce the *encrypted result file* `demographic-query-result`.

Some important options:

```
Option | Description
 -d    | data input format (Options: base)
 -ds   | data schema file name
 -i    | Data file name
 -p    | execution method (options are: standalone, kafka, mapreduce)
 -qs   | query schema file name
 -q    | encrypted query file name
 -o    | encrypted results file name
 
** Notes: ** 
```
 
### Decrypting the server response
To decrypt the query result, we copy `demographic-query-result` to the querier side and then run the following command to decrypt the result using the private key in the querier file:

```
ENQUERY_HOME="../.."
java -cp -Djava.library.path=$ENQUERY_HOME/lib/native $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.querier.wideskies.QuerierDriver -a decrypt -qf demographic-querier -i demographic-query-result -nt 1 -o demographic-plain-result -qs queryschema.xml -ds dataschema.xml
```

At this point the plaintext result file `demographic-plain-result` should be created:

```
$cat demographic-plain-result 
{"event_type":"simple query","query_id":"4c0291a8-29e2-11e7-93ae-92361f002671","match":"Bob","name":["Bob",""],"age":25}
```

Some important command line options:

```
| Option | Description |
| ------ | --------------------- |
| -a     | Action, decrypt | 
| -qf    | The demographic-querier file that was generated during query creation. Contains necessary information in order to decipher the query result. | 
| -i     | The encrypted query result file. | 
| -nt    | Number of threads. | 
| -o     | The file name where to store the clear text query result. | 
| -qs    | The Query Schema file. | 
| -ds    | The Data Schema file. | 

**Notes: **

```


## Example Run in Hadoop Map/Reduce Mode

The phone example shows the details for executing an Encrypted Query in Hadoop Map/Reduce mode.

Execute the phone query by issuing the following command: `./run_phone_query.sh phone mapreduce` 

The parameter `phone` is the name of the query and `mapreduce` is the method the responder will run in.

```
# *******************
# This script will execute all 3 parts if the encrypted search
# 1st calling generate_query to generate the encrypted query
# 2nd call the responder to generate encrypted resultset
# 3rd decrypt the resultset into plain text
#
#*************************************
#!/bin/bash

die () {
  echo >&2 "$@"
  echo "Usage: run_encrypted_query.sh [query name] [method (standalone or mapreduce)]"
  exit 1
}

[ "$#" -eq 2 ] || die "2 arguments required, $# provided"

NAME=$1
METHOD=$2

./generate_query.sh $NAME
./run_responder.sh $NAME $METHOD
./decrypt_result.sh $NAME
cat $NAME-plain-result
```

As you can see the script will check that there are 2 parameters then generate the encrypted query, run the responder, decrypt the response, and show the plain text result.

Generating the Query script:

```
# *******************
# This script will generate the encrypted phone query.
#*************************************
#!/bin/bash

die () {
  echo >&2 "$@"
  echo "Usage: generate_query.sh [query name]"
  exit 1
}

[ "$#" -eq 1 ] || die "1 argument required, $# provided"

NAME=$1

ENQUERY_HOME="../.."

java -Djava.library.path=$ENQUERY_HOME/lib/native/ -cp $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.querier.wideskies.QuerierDriver -a encrypt \
 -b 32 -c 128 -dps 16 -hb 15 -pbs 3072 -i $NAME.txt -qt "$NAME query" -nt 4 \
 -qs queryschema_$NAME.xml -ds dataschema_$NAME.xml -m fastwithjni -o $NAME
```

The run responder script will execute the encrypted query in Hadoop mapreduce mode.  The script can also run the query in standalone mode 
if that argument was used instead.   When running an encrypted query in mapreduce mode the script 1st uploads some files to Hadoop:

1. Data Schema file
2. Query Schema file
3. Encrypted Query file
4. Data file  (Thought this file would most likely already be in Hadoop..)
5. Shared library for the responder

_Note: refer to the run responder script that show the commands to upload files to hadoop_

At the of the script it copies the result file from hadoop into the local folder. 

```
# *******************
# This script will execute the responder.
# @Args
#    query name
#    method
#
#*************************************
#!/bin/bash

die () {
  echo >&2 "$@"
  echo "Usage: generate_query.sh [name] [method (standalone or mapreduce)]"
  exit 1
}

[ "$#" -eq 2 ] || die "2 arguments required, $# provided"

NAME=$1
METHOD=$2

ENQUERY_HOME="../.."

if [ "$METHOD" == "standalone" ]; then
   echo "Running Standalone"
   java -Djava.library.path=$ENQUERY_HOME/lib/native/ -cp $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.responder.wideskies.ResponderDriver \
      -d base -ds ./dataschema_$NAME.xml -i ./datafile_$NAME.json -p $METHOD -qs ./queryschema_$NAME.xml -q ./$NAME-query -o ./$NAME-query-result
else
   echo " Running Hadoop Map/Reduce"
   fn=$NAME-query
   if [ -f $fn ] ; then
     echo "Copying query files to Hadoop.."
     bfn=`basename $fn` #trim path from filename
     hdfs dfs -mkdir -p /user/enquery/$NAME/
     hdfs dfs -mkdir -p /user/enquery/lib/
     hdfs dfs -put -f $fn /user/enquery/$NAME/$bfn
     hdfs dfs -put -f ./dataschema_$NAME.xml /user/enquery/$NAME/
     hdfs dfs -put -f ./queryschema_$NAME.xml /user/enquery/$NAME/
     hdfs dfs -put -f ./datafile_$NAME.json /user/enquery/$NAME/
     hdfs dfs -put -f $ENQUERY_HOME/lib/native/libresponder.so /user/enquery/lib/
     hdfs dfs -ls /user/enquery/$NAME/$bfn
     success=$? #check whether file landed in hdfs
     if [ $success ] ; then
       echo "Successfully copied query files for $fn into Hadoop"
     fi
   fi

   hadoop jar $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.responder.wideskies.ResponderDriver \
       -bif org.enquery.encryptedquery.inputformat.hadoop.json.JSONInputFormatBase \
       -d base -ds /user/enquery/$NAME/dataschema_$NAME.xml -i /user/enquery/$NAME/datafile_$NAME.json -p $METHOD \
       -mh 16384 -qs /user/enquery/$NAME/queryschema_$NAME.xml -q /user/enquery/$NAME/$NAME-query -nr 10 -o /user/enquery/$NAME/$NAME-query-result

   echo "Responder complete, copying encrypted result.."
   rm -rf $NAME-query-result
   hdfs dfs -get  /user/enquery/$NAME/$NAME-query-result
   success=$? #check whether result set was copied from hdfs
   if [ $success ] ; then
      echo "Successfully copied Result Set to local folder"
   fi

fi
```

Some important command line options for the responder:

```
| Option | Description |
| ------ | --------------------- |
| -bif   | The input format class, which in this case, corresponds to the JSON file format of our data file. | 
| -d     | Input data format use 'base'. | 
| -ds    | The fully qualified schema file describing the data file.  This path is in HDFS. | 
| -i     | The fully qualified filename of the data file in HDFS.   In our case we point to the JSON data file. | 
| -p     | Method to run the query, in this case we want to run in mapreduce distributed mode. | 
| -qs    | The fully qualified filename of Query Schema file in HDFS. | 
| -q     | The fully qualified encrypted query file in HDFS. | 
| -o     | The fully qualified file name where to store the encrypted result in HDFS. | 

**Notes: **
  One option that is set in the properties file (it could be set with command line options too is the algorithm to use
  when computing the response.  There is a default choice but the user can specify the algorithm using -ra option
  (or responderEncryptColumn configuration property).  For the best performance it is recommended for the user to
  use if possible either "derooijjni" or "yaojni", which are many times faster than the corresponding pure Java implementations.  
  Options for this are: (basic, derooij, derooijjni, yao, yaojni)
  
  -ra basic
  
  This is algorithmically the simplest method but is generally slower than the (default) derooij method, except when the hash 
  bit size is small or when there is only a small amount of data to be processed.

  -ra derooij  (Default option)

  The De Rooij algorithm performs somewhat faster than the basic method for moderate to high hash bit sizes. 

  -ra derooijjni

  This option uses the native library libresponder.so to perform the DeRooij algorithm at a much higher speed.  
  Currently this algorithm is only available for data partition bit size <= 24.

  -ra yao

  The Yao algorithm performs faster than the De Rooij when the hash bit size is at least the data partition bit size. 
  The amount of memory this method uses increases with the data partition bit size, so currently this algorithm is only 
  available if the data partition bit size is at most 16.

  -ra yaojni

  This option uses the native library libresponder.so to perform the Yao algorithm at a much higher speed.  Currently this 
  algorithm is only available if the data partition bit size is at most 16.
  
  The jni versions of these options use a shared library to run.  This library must be in the java shared library path if 
  running in standalone or loaded into hadoop if running in mapreduce mode.
  

```

Finally the main script will execute the decrypt script to decrypt the result file:

```
# *******************
# This script will decrypt the result set generated by the responder and
# place the results in the output file designated by the -o parameter
#
#*************************************
#!/bin/bash

die () {
  echo >&2 "$@"
  echo "Usage: decrypt_result.sh [query name]"
  exit 1
}

[ "$#" -eq 1 ] || die "1 argument required, $# provided"

NAME=$1

ENQUERY_HOME="../.."

java -cp $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.querier.wideskies.QuerierDriver  -a decrypt \
-qf $NAME-querier -i $NAME-query-result -nt 1 -o $NAME-plain-result -qs queryschema_$NAME.xml -ds dataschema_$NAME.xml
```


Decrypted query results:

```
{"event_type":"phone query","query_id":"7d0d11cb-1313-4720-88be-3e0864ccd1a0","Caller #":"275-913-7889","Callee #":"385-145-4822","Date\/Time":"03\/19\/2016 06:35","match":"275-913-7889"}
{"event_type":"phone query","query_id":"7d0d11cb-1313-4720-88be-3e0864ccd1a0","Caller #":"649-186-8058","Callee #":"146-727-7104","Date\/Time":"10\/26\/2016 06:34","match":"649-186-8058"}
{"event_type":"phone query","query_id":"7d0d11cb-1313-4720-88be-3e0864ccd1a0","Caller #":"797-844-8761","Callee #":"667-828-9241","Date\/Time":"04\/09\/2016 10:53","match":"797-844-8761"}
```

## Example run in Streaming mode

The kafka-stream example shows Encrypted Query executing a query against data from a kafka stream in real-time.

To run this example you need to have kafka installed with a topic named 'stream-test' available.  The example will generate data in a kafka-producer application 
that feeds data into the stream-test topic.  Two core configuration options for Encrypted Query of stream data are the search window (time period to search the stream) and how many instances you want to search.   After each instance a result file is returned.   

To execute the streaming example start the script: `run_streaming_example.sh`

This script will generate the encrypted query, start the kafka producer, start the responder to search the data, then after each 30sec time window expires it will create a result file.  Two 20sec windows will be queried.

You can change configuration options by editing the config/responder.properties file
 

