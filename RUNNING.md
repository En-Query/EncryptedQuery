# Running Encrypted Query

There are three steps to running Encrypted Query:
1. Create an encrypted query (on the querier side)
2. Run the query against the data (on the server side)
3. Decrypt the server response (on the querier side)

Currently Step 2 supports two different modes: standalone and Hadoop.  Steps 1 and 3 do not depend on the mode.


## Test Data

We describe a test run in each mode.  Both test runs use the following Data File (datafile.json):
```
{ "name": "Alice", "age": "31", "children": [ "Zack", "Yvette" ] }
{ "name": "Bob",   "age": "25", "children": [ "Xavier", "Wendy" ] }
{ "name": "Chris", "age": "43", "children": [ "Donna" ] }
{ "name": "Donna", "age": "19", "children": [ ] }
```

Here is the schema (dataschema.xml) describing our data file:
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

The above two files are static and represent our database against which we can run any number of queries.

For our query we also need a schema file as well as a  containing a query UUID and the selectors (search terms) we are interested in.

Query schema file (queryschema.xml):
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

Query file (query.txt):
```
4c0291a8-29e2-11e7-93ae-92361f002671
Bob
```
The first line is the the id of query, and it must be a UUID value.  Our value was generated using the online UUID generator https://www.uuidgenerator.net/.  The subsequent lines contain the term(s) we are searching for, which will be matched to the field indicated by the selectorName tag in the queryschema.xml file shown above.

So, this query is looking for a record with name file = "Bob" and returning the name and the age.



## Sample Run in Standalone Mode

###Creating the encrypted query file:
```
java org.enquery.encryptedquery.querier.wideskies.QuerierDriver -a encrypt -c 128 -dps 8 -hb 12 -pbs 3072  -i query.txt -qt "simple query" -nt 1 -qs queryschema.xml -ds dataschema.xml -o demographic
```

Where:

| Option | Description |
| -------- | --------------------- |
| -a | Action to perform, in this case 'encrypt' to create an encrypted query. |
| -c | Certainty of prime generation for Paillier |
| -dps | Partition bit size in data partitioning |
| -hb | Bit size of keyed hash |
| -pbs | Paillier modulus size N |
| -i | The file name containing the concrete query. | 
| -qt | The name of the schema describing this query.  This value needs to match the schemaName value in the query schema defined earlier. | 
| -nt | Number of threads. | 
| -qs | Comma separated list of query schema file names.  In this case, the path to the queryschema.xml file created earlier in HDFS. | 
| -ds | Comma separated list of data schema file names.  In this case, the path to the dataschema.xml file created earlier in HDFS. | 
| -o | Prefix of output files names. | 

###Run the encrypted query in standalone mode

```
java org.enquery.encryptedquery.responder.wideskies.ResponderDriver -d base -ds ./dataschema.xml -i ./datafile.json -p standalone -qs ./queryschema.xml -q ./demographic-query -o ./demographic-query-result
```

###Decrypting the server response
```
java org.apache.enquery.encryptedquery.wideskies.QuerierDriver -a decrypt -qf demographic-querier -i demographic-query-result -nt 1 -o demographic-plain-result -qs queryschema.xml -ds dataschema.xml
```

Where:

| Option | Description |
| -------- | --------------------- |
| -a | Action, decrypt | 
| -qf | The demographic-querier file that was generated during query creation. Contains necessary information in order to decipher the query result. | 
| -i | The encrypted query result file. | 
| -nt | Number of threads. | 
| -o | The file name where to store the clear text query result. | 
| -qs | The Query Schema file. | 
| -ds | The Data Schema file. | 


At this point the demographic-plain-result file should be created:

```
$cat demographic-plain-result 
{"event_type":"simple query","query_id":"4c0291a8-29e2-11e7-93ae-92361f002671","match":"Bob","name":["Bob",""],"age":25}
```


## Sample Run in Hadoop Mode

These are notes about running a simple Encrypted query in MapReduce mode using the command line tools.

We upload our database (data and schema files) and the query schema into HDFS, lets say under the current user's home encryptedquery directory.

```
hadoop fs -put datafile.json   encryptedquery/
hadoop fs -put dataschema.xml  encryptedquery/
```

We create the encrypted query object as before:
```
java org.enquery.encryptedquery.querier.wideskies.QuerierDriver -a encrypt -c 128 -dps 8 -hb 12 -pbs 3072  -i query.txt -qt "simple query" -nt 1 -qs queryschema.xml -ds dataschema.xml -o demographic
```

Two files are created locally:

        demographic-query    (the actual encrypted query to be sent for execution)
        demographic-querier  (encryption information used to decrypt the query results)

We upload the encrypted query (not the querier!) and the query schema to HDFS:

```
hadoop fs -put queryschema.xml encryptedquery/
hadoop fs -put demographic-query encryptedquery/
```

Now you can run this encrypted query in the Hadoop cluster by issuing this command:

```
hadoop jar encryptedquery.jar org.enquery.encryptedquery.responder.wideskies.ResponderDriver -bif org.enquery.encryptedquery.inputformat.hadoop.json.JSONInputFormatBase -d base -ds /user/cloudera/encryptedquery/dataschema.xml -i /user/cloudera/encryptedquery/datafile.json -p mapreduce -qs /user/cloudera/encryptedquery/queryschema.xml -q encryptedquery/demographic-query -o encryptedquery/demographic-query-result
```

Where:

| Option | Description |
| -------- | --------------------- |
| -bif | The input format class, which in this case, corresponds to the JSON file format of our data file. | 
| -d | Execution mode.  In this case we use 'base' for distributed MapReduce mode.  Other options are 'elasticsearch', or 'standalone'. | 
| -ds | The schema file describing the data file.  This path is in HDFS. | 
| -i | The data file path in HDFS.   In our case we point to the JSON data file. | 
| -p | Platform to run the query, in this case we want to run in MapReduce distributed mode. | 
| -qs | The path to the file containing the Query Schema in HDFS. | 
| -q | The path to the encrypted query file in HDFS, which we uploaded earlier. | 
| -o | The file name where to store the encrypted result in HDFS. | 

Download the Encrypted Query result from HDFS to local current directory:

```
hadoop fs -get encryptedquery/demographic-query-result .
```

Decrypt query results:

```
java org.enquery.encryptedquery.querier.wideskies.QuerierDriver -a decrypt -qf demographic-querier -i demographic-query-result -nt 1 -o demographic-plain-result -qs queryschema.xml -ds dataschema.xml
```


At this point the demographic-plain-result file should be created:

```
$cat demographic-plain-result 
{"event_type":"simple query","query_id":"4c0291a8-29e2-11e7-93ae-92361f002671","match":"Bob","name":["Bob",""],"age":25}
```




