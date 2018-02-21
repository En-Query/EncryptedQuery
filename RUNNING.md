# Running Encrypted Query

There are three steps to running Encrypted Query:
1. Create an encrypted query (on the querier side)
2. Run the query against the data (on the server side)
3. Decrypt the server response (on the querier side)

Currently Step 2 supports two different modes: standalone and Hadoop.  Steps 1 and 3 do not depend on the mode.


## Test Data

Below we describe how to perform a test run in each mode.  Both test runs use the following two files which represent the static database on the server side.  These files are available in the `examples/simple/` folder.

The *data file* `datafile.json` contains the data that the server will use to process our query, with one JSON record per line:
```
{ "name": "Alice", "age": "31", "children": [ "Zack", "Yvette" ] }
{ "name": "Bob",   "age": "25", "children": [ "Xavier", "Wendy" ] }
{ "name": "Chris", "age": "43", "children": [ "Donna" ] }
{ "name": "Donna", "age": "19", "children": [ ] }
```

The *data schema file* `dataschema.xml` describes our data file:
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

Our query will consist of a *query schema file* specifying which data field we are searching on and which data fields we wish to retrieve, as well as a *selector file* specifying the specific selectors (search terms) we are interested in.  Our test query is looking for records with name = "Bob" and is asking for both the name and the age fields for such records.  Both files are also available in the `examples/simple/` folder.

Query schema file (`queryschema.xml`):
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

Query file (`query.txt`):
```
4c0291a8-29e2-11e7-93ae-92361f002671
Bob
```
The first line is a UUID value that represents the id of the query.  Our value was generated using the online UUID generator https://www.uuidgenerator.net/.  The subsequent lines contain the term(s) we are searching for, which will be matched to the field indicated by the selectorName tag in the queryschema.xml file shown above.



## Sample Run in Standalone Mode

### Creating the encrypted query file:
Creating an encrypted query is done on the querier side.  The first step is to prepare a query schema file and a selector file describing the field we wish to search on and the fields we wish to retrieve, together with the specific selector values we are interested in.  For this run we will use the files `queryschema.xml` and `query.txt` as above.

Next we create the encrypted query:
```
java -cp encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.querier.wideskies.QuerierDriver -a encrypt -c 128 -dps 8 -hb 12 -pbs 3072  -i query.txt -qt "simple query" -nt 1 -qs queryschema.xml -ds dataschema.xml -o demographic
```
This should produce two new files, a *query file* `demographic-query` and a *querier file* `demographic-querier`.  The query file contains the encrypted query to be used by the server to perform our private query.  The querier file contains sensitive private key information needed to decrypt the server response, and so should not be sent to the server.

Some important options:

| Option | Description |
| -------- | --------------------- |
| -a | Action to perform, in this case 'encrypt' to create an encrypted query. |
| -c | Certainty of prime generation for Paillier |
| -dps | Partition bit size in data partitioning must be a multiple of 8 |
| -hb | Bit size of keyed hash |
| -pbs | Paillier modulus size N |
| -i | The file name containing the concrete query. | 
| -qt | The name of the schema describing this query.  This value needs to match the schemaName value in the query schema defined earlier. | 
| -nt | Number of threads. | 
| -qs | Comma separated list of query schema file names.  In this case, the path to the queryschema.xml file created earlier in HDFS. | 
| -ds | Comma separated list of data schema file names.  In this case, the path to the dataschema.xml file created earlier in HDFS. | 
| -o | Prefix of output files names. | 
| -m | Query generation method.  May be "default" (the default) or "fast". |

**Note:** For higher hash bit sizes (`-hb`), query generation can take a while.  In this case this step can be sped up by increasing the number of threads (`-nt`) or using the fast query generation method (`-m fast`).

### Running the encrypted query (standalone mode)
To perform the query, we copy the encrypted query file `demographic-query` to the server side and then run:
```
java -cp encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.responder.wideskies.ResponderDriver -d base -ds ./dataschema.xml -i ./datafile.json -p standalone -qs ./queryschema.xml -q ./demographic-query -o ./demographic-query-result
```
This should produce the *encrypted result file* `demographic-query-result`.

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




