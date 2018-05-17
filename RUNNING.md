# Running Encrypted Query


There are three steps to running Encrypted Query:
1. Create an encrypted query (on the querier side)
2. Run the Responder to query against the data (on the server side)
3. Decrypt the server response (on the querier side)

Currently Encrypted query supports querying against data in JSON format.  The Encrypted Query Responder (Server side piece that actually queries the data) supports three different modes: standalone, kafka streaming and Hadoop map/reduce.  Steps 1 and 3 do not depend on the mode.


# Pre-requisites
To build Encrypted Query will require a Oracle Java 1.8x JDK and Apache Maven.  It will also require a gcc compiler.

To build download the project from github and build with the command: mvn clean install -P native-libs

This will create a distribution tar file (encryptedquery-1.0.0-SNAPSHOT-dist.tar.gz) in the target folder.  Un-tar this file into a working folder.
 
To run Encrypted Query you will need to have java 1.8x runtime installed on the server.

If you plan on running in streaming mode you will also need a kafka server running.

To run in Hadoop map/reduce mode will require a Hadoop installation with map/reduce.


Installing of these pre-requisites  are beyond the scope of this document but information can be found:

Java JDK:  http://www.oracle.com/technetwork/java//jdk8-downloads-2133151.html

Apache kafka: https://kafka.apache.org/downloads

Hadoop: http://hadoop.apache.org/

## Required Files

Below we describe how to perform a test runs in each mode.  The following files are required to execute an Encrypted Query:

*Data Schema file* - This file describes the data to be queried against.  Each field in the JSON record is described.

*Query Schema file* - This file identifies the query name, data schema file, and which fields from the data schema are to be retrieved in the result as well as the field to query against(Selector field).

*Selector data file* - This file contains a unique id for the query as well as specifying the specific selectors (search terms) we are interested in.

*Data file* - This is the actual data we will be quering against

# Examples of each file:

*Data Schema file* (`dataschema.xml`):

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

*Query Schema file* (`queryschema.xml`):

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

*Selector file* (`query.txt`):

```
4c0291a8-29e2-11e7-93ae-92361f002671
Bob
```
The first line is a UUID value that represents the id of the query.  Our value was generated using the online UUID generator https://www.uuidgenerator.net/.  The subsequent lines contain the term(s) we are searching for, which will be matched to the field indicated by the selectorName tag in the queryschema.xml file shown above.
  

The *Data file* `datafile.json` contains the data that the server will use to process our query, with one JSON record per line:

```
{ "name": "Alice", "age": "31", "children": [ "Zack", "Yvette" ] }
{ "name": "Bob",   "age": "25", "children": [ "Xavier", "Wendy" ] }
{ "name": "Chris", "age": "43", "children": [ "Donna" ] }
{ "name": "Donna", "age": "19", "children": [ ] }
```

# Running the Simple Query example
In the Simple Query example our test query is looking for records with name = "Bob" and is asking for both the name and the age fields for such records.  The simple example files are available and can be executed from the `examples/simple/` folder.

To run the simple query enter the command: ./run_simple_query.sh

This script will execute the *Generate Query* script which builds the query.  When this is finished two files will be created:  

*demographic-query*  - This is the encrypted query file to be used by the responder to query the data
*demographic-querier* - This is the key file to be used to decrypt the result file from the responder

It then executes the *Responder* script which will execute the query against the data file in standalone mode.  This will create the result file:

*demographic-query-result*  - This file is the encrypted result file.

Finally the script will execute the *Decrypt* script which will take the result file and convert it into plain text.  This is output to the file:

*demographic-plain-result*  - Plain Text of the results from the query.
 
This run will show log messages as it is running and will output the following result line at the end:

```
{"event_type":"simple query","query_id":"4c0291a8-29e2-11e7-93ae-92361f002671","match":"Bob","name":["Bob",""],"age":25}
```


### Creating the encrypted query file:
Creating an encrypted query is done on the enclave side.  The first step is to prepare a data schema file, query schema file, and a selector file describing the field we wish to search on and the fields we wish to retrieve, together with the specific selector values we are interested in.  For this run we will use the files `dataschema.xml`, `queryschema.xml`, and `query.txt` as above.

Next we create the encrypted query:

```
ENQUERY_HOME=/folder of the expanded distribution file/
java -Djava.library.path=$ENQUERY_HOME/lib/native/ -cp $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.querier.wideskies.QuerierDriver -a encrypt -c 128 -dps 8 -hb 12 -pbs 3072  -i query.txt -qt "simple query" -nt 1 -qs queryschema.xml -ds dataschema.xml -m default -o demographic
```
This should produce two new files, a *query file* `demographic-query` and a *querier file* `demographic-querier`.  The query file contains the encrypted query to be used by the server to perform our private query.  The querier file contains sensitive private key information needed to decrypt the server response, and so should not be sent to the server.

Some important options:

```
| Option | Description |
| ------ | --------------------- |
| -a     | Action to perform, in this case 'encrypt' to create an encrypted query. |
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

**Note:** For higher hash bit sizes (`-hb`), query generation can take a while.  
          If extra cores are available, this step can be sped up by increasing the number of threads (e.g. `-nt 4`).
          In addition, the "fast" and "fastwithjni" methods should be significantly faster than the default method.
```

### Running the Responder (standalone mode)
To perform the query, we copy the encrypted query file, data schema, & query schema files to the server side and then run:

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
 
### Decrypting the server response
To decrypt the query result, we copy `demographic-query-result` to the querier side and then run the following command to decrypt the result using the private key in the querier file:
```
java -cp encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.querier.wideskies.QuerierDriver -a decrypt -qf demographic-querier -i demographic-query-result -nt 1 -o demographic-plain-result -qs queryschema.xml -ds dataschema.xml
```
At this point the plaintext result file `demographic-plain-result` should be created:
```
$cat demographic-plain-result 
{"event_type":"simple query","query_id":"4c0291a8-29e2-11e7-93ae-92361f002671","match":"Bob","name":["Bob",""],"age":25}
```


Some important command line options:

| Option | Description |
| -------- | --------------------- |
| -a | Action, decrypt | 
| -qf | The demographic-querier file that was generated during query creation. Contains necessary information in order to decipher the query result. | 
| -i | The encrypted query result file. | 
| -nt | Number of threads. | 
| -o | The file name where to store the clear text query result. | 
| -qs | The Query Schema file. | 
| -ds | The Data Schema file. | 



## Sample Run in Hadoop Mode

These are notes about running a simple Encrypted query in MapReduce mode using the command line tools.

We upload our database (data and schema files) and the query schema into HDFS, lets say under the current user's home encryptedquery directory.

```
hdfs dfs -put datafile.json   /user/encryptedquery/
hdfs dfs -put dataschema.xml  /user/encryptedquery/
```

We create the encrypted query object as before:
```
java -cp encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.querier.wideskies.QuerierDriver -a encrypt -c 128 -dps 8 -hb 12 -pbs 3072  -i query.txt -qt "simple query" -nt 1 -qs queryschema.xml -ds dataschema.xml -o demographic
```

Two files are created locally:

        demographic-query    (the actual encrypted query to be sent for execution)
        demographic-querier  (encryption information used to decrypt the query results)

We upload the encrypted query (not the querier!) and the query schema to HDFS:

```
hdfs dfs -put queryschema.xml /user/encryptedquery/
hdfs dfs -put demographic-query /user/encryptedquery/
```

Now you can run this encrypted query in the Hadoop cluster by issuing this command:

```
hadoop jar encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.responder.wideskies.ResponderDriver -bif org.enquery.encryptedquery.inputformat.hadoop.json.JSONInputFormatBase -d base -ds /user/encryptedquery/dataschema.xml -i /user/encryptedquery/datafile.json -p mapreduce -qs /user/encryptedquery/queryschema.xml -q /user/encryptedquery/demographic-query -o /user/encryptedquery/demographic-query-result
```

Where:

| Option | Description |
| -------- | --------------------- |
| -bif | The input format class, which in this case, corresponds to the JSON file format of our data file. | 
| -d | Input data format use 'base'. | 
| -ds | The fully qualified schema file describing the data file.  This path is in HDFS. | 
| -i | The fully qualified filename of the data file in HDFS.   In our case we point to the JSON data file. | 
| -p | Method to run the query, in this case we want to run in mapreduce distributed mode.  Other option is 'standalone' | 
| -qs | The fully qualified filename of Query Schema file in HDFS. | 
| -q | The fully qualified encrypted query file in HDFS, which we uploaded earlier. | 
| -o | The fully qualified file name where to store the encrypted result in HDFS. | 

Download the Encrypted Query result from HDFS to local current directory:

```
hdfs dfs -get /user/encryptedquery/demographic-query-result .
```

Decrypt query results:

```
java -cp encryptedquery-1.0.0-SNAPSHOT-exe.jar  org.enquery.encryptedquery.querier.wideskies.QuerierDriver -a decrypt -qf demographic-querier -i demographic-query-result -nt 1 -o demographic-plain-result -qs queryschema.xml -ds dataschema.xml
```


At this point the demographic-plain-result file should be created:

```
$cat demographic-plain-result 
{"event_type":"simple query","query_id":"4c0291a8-29e2-11e7-93ae-92361f002671","match":"Bob","name":["Bob",""],"age":25}
```




