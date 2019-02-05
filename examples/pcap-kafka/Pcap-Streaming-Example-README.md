## Streaming example using Pcap Data in json format
Encrypted Query can search data in in a Kafka stream.   Users set the total length of time for the search and the window length.   The responder will search the kafka stream for the total time given and return a result set after each time window has been processed.  The Network Packet Capture (pcap) data for this example contains data that was captured using wireshark and converted to json representation.  This example simulates a streaming environment by steadily reading data from the file and writing it to a kafka stream.  In a live situation this may be a continuous stream of data coming from an application that outputs to a kafka stream.  For information about Kafka and setting up a stream for processing by Encrypted Query you can review the documentation for [Apacke Kafka](https://kafka.apache.org)
### Prerequisites
* Install and Start Kafka on a server accessible to the responder.   
* Install and Stark Flink.   Be sure the Flink job manager is on the same server as the responder.
* Setup Kafka (Below)
* Build Streaming app to load data info Kafka for this example.(Below)
* Follow the instructions in the Deployment-README.md document for preparing the examples.

#### Setup Kafka
* Kafka uses a term 'Topic' which is used to group data into a stream.  A kafka Producer writes data into the topic and a Kafka Consumer will pull data from the stream.   Kafka stores data for a specified period of time.  As the consumer reads data from the kafka stream, it marks what has already been read by using an offset value.   If you need to re-run this example you will need to delete/re-create the topic or reset the offsets for the Group Id.  If you reset the offset then the next run will consume all records already written to kafka as well as any new records written during the query search.  Below are some basic commands for setting up and managing a kafka topic.

###### Create a Kafka Topic
    #> kafka-topics.sh --zookeeper <zookeeper IP:port> --topic pcap-feed --create --partitions 1 --replication-factor 1
This will create a topic `pcap-feed` in Kafka with 1 partition.  Substitute the <zookeeper IP:port> with the ones used setting up kafka.

Here are some other Kafka commands that might be useful:
> Delete a Kafka Topic
    #> kafka-topics.sh --zookeeper <zookeeper IP:port> --topic pcap-feed --delete    
This will delete the `pcap-feed` topic from kafka

> Read data in a topic
    #> kafka-console-consumer.sh --bootstrap-server <kafkaBootstrapServer IP:port> --topic pcap-feed --from-beginning
This will continuously read data in the `pcap-feed` topic starting from the earliest stored to current.  Hit `CTRL-C` to exit and stop reading.  After you exit it shows how many records were listed.

> Reset the offset for a group Id
    #> kafka-consumer-groups.sh --bootstrap-server <kafkaBootStrapServer IP:port> --group test --topic pcap-feed --execute --reset-offsets --to-earliest
This will reset the offset (Next record to read) on the `pcap-feed` topic to the earliest offset for the group `test` 

#### Build the Streaming app
The streaming app used for this example is delivered as a maven java project.   Navigate to the `/home/EncryptedQuery/examples/pcap-kafka/stream-generator/` folder.
1) Untar the app.  (#> tar -xvf stream-generator.tar.gz)
2) cd json-reader-kafka-producer
3) Build the app: (#> mvn clean install)
4) cd ..
5) Edit the json-reader.properties file and set the Kafka.bootstrap.server parameter to the correct IP:port for the kafka server

### Configure Responder Data Schema
The included file `pcap-data-schema.xml` contains the Data Schema describing some of the Pcap data fields.  This file should have been copied to the responder data-schema inbox during the Deployment of the examples.  If not copy this file into the Responder inbox directory (by default _/opt/enquery/dataschemas/inbox/_)

	cp /home/EncryptedQuery/examples/responder-files/data-schemas/pcap-data-schema.xml /opt/enquery/dataschemas/inbox/  
	
The responder periodically checks this folder for new/updated data schemas and will injest the data schema to make it available to query.

### Configure a Data Source using Flink engine on the Kafka pcap-feed topic.

The example data source for this example is in the file `/home/EncryptedQuery/examples/responder-files/data-source-configurations/org.enquery.encryptedquery.responder.flink.kafka.runner.FlinkKafkaQueryRunner-Pcap.cfg` and should have been copied to the responder config folder in the deployment.  If not copy this config file to the `responder/etc/` folder.  

	cp /home/EncryptedQuery/examples/responder-files/data-source-configurations/org.enquery.encryptedquery.responder.flink.kafka.runner.FlinkKafkaQueryRunner-Pcap.cfg   /opt/enquery/responder/etc
Edit the data source configuration file to set the location of the Flink installation.
(Note: This example also provides a standalone data file and data source to run in standalone mode).   The data used for this example is the same between the standalone and streaming methods.)

### Import the REST interface calls into Postman to run the example.
* [Postman App](https://www.getpostman.com/apps)

Copy the /home/EncryptedQuery/examples/rest-collections/pcap_postman_collection.json to where it can be accessed by the Postman app.
Start the Postman app and click on the Import button.  Select the `pcap_postman_collection.json` file to load.
** Note: Substitute the IP or DNS name for the querier server being accessed in place of the IP address listed in the URLs.

### Running the Example
1) Select the `GET DataSchemas` REST call.  Click `SEND` to execute the call. This will return a list of available data schemas to query on.  Select the id for the `Pcap` data schema.
2) Select the `POST Create QuerySchema` REST call.  Change the `data schema id` in the URL with the data schema id from step 1.  Click `SEND` to create the Query Schema.
3) You may optionally execute the `Get QuerySchema Detail` REST call to view the details of the query schema.
4) Select the `POST Encrypt Query` REST call.   Change the data and query schema id's with the ones returned from steps 1 and 2 above.  Click `SEND` to execute the REST call.  The query is now being encrypted on the server.   Depending on the hashBitSize set during Querier installation, encryption may take a few minutes.
5) You may optionally execute the `GET Query Detail` REST call to check the status of the query encryption.   Change the relevant ids with those given from steps 1, 2, & 4 to get the status of the correct query.
6) Select the `GET DataSources` REST call.  Substitute the data schema id from step 1 in the URL.   Click `SEND`.   This will return a list of Data Sources available for this Data Schema.  Select the id for the data source to execute the query against.  
7) Select the `POST Schedule Query` REST call.  Substitute the data schema, query schema, query id's from steps 1, 2, & 4 above in the URL.  Substitute the data source id from step 6 in the body.  Set the startTime to execute the query.  (Note: The time is UTC time).  The other parameters are set to run the query for 60 seconds total with 20 second windows.  The maxHitsPerSelector is set at 1000 which means the  query will only select the 1st 1000 hashs found for each hash.  Before you click `SEND` to schedule the query start the data stream.
8) Start the data stream from the EncryptedQuery/examples/pcap-kafka/stream-generator/ folder.  Execute the command:
    #> java -jar json-reader-kafka-producer-0.0.1-SNAPSHOT-jar-with-dependencies.jar json-reader.properties

This example simulates a datastream by loading data from a file into Kafka at a selected rate.  Default rate of streaming is 5 records per second.  It will load data for ~2min50sec about 749 records total.

9) You can monitor the progress of Flink using the Apache Flink Web Dashboard in a web browser.   URL: http://<flink server ip>:8081
10) After Flink has finished running Select the `GET Results` REST Call.  Update the id's in the URL with the proper ids from steps 1, 2, 4, & 7 above.  Click `SEND` to initiate the REST call.   This will generate a list of result files.   You must download and decrypt each result file separatly to see the results from each search window.
11) Select the `POST Retrieve Results` REST call.  Update the id's in the URL with the proper ids from steps 1, 2, 4, 7 & 10 above.  Click `SEND` to execute the call.   Repeat this for each of the Result file id's. and take note of the retrieval id generated for each.
12) Select the `POST Decrypt` REST Call and update the id's in the URL with the proper ids from steps 1, 2, 4, 7, 10, & 11 above.  Click `SEND` to execute the decryption of the result file.



