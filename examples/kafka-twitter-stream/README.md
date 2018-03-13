This is a streaming example to test the Encrypted Query in kafka streaming mode.  Using a Twitter feed for the stream.

Pre-Requisites:
Install Kafka/zookeeper using the default ports
create the kafka topic "twitter-feed"
Generate the encrypted query jar file
Update the encryptedquery.properties to point to the local configuration folder 
Update the shell scripts to point to the jar file.

(Note:  The twitter feed will requre a set of keys for access to the twitter api.  These are: consumer key, consumer secret, Access Token, and Access Token Secret )
!! Once you have these values, update the /examples/kafka-twitter-stream/kafka-producer/twitter-kafka-producer.properties file with your values !!

Setup:
This demo will stream tweets from twitter that have been posted under the hashtag #Brexit.   It will stream for 2 - 3minute windows and place the results
into stream-demo-result-1 and stream-demo-result-2 files.  

Depending on when you run this demo, you may need to change the Hashtag to pull from as #Brexit may no longer be popular.


Execution:
Execute the run_twitter_streaming_example.sh script to perform a complete exercise (Update configuration, Generate Query, Start Streaming, Run Responder, & Decrypt Result)
You can also execute these steps individually by executing:
generate_query.sh                       <-- Generate the Query 
start_stream.sh                         <-- Start the kafka producer to stream twitter data 
run_responder_stream.sh                 <-- Run the responder 
decrypt_result.sh <name of result file> <-- Decrypt the result


The source code for the Twitter feed has been included with this demo.  It is a simple producer that will spawn 'x' number of producer threads
to publish records into a kafka topic.  The source for the records is from a connection to twitter using the Hosebird twitter api.

To compile the kafka-twitter-producer:
        cd /examples/kafka-twitter-stream/kafka-producer/src/
	tar -xvf Kafka-Twitter-Producer.tar       <-- Extract from the tar file
	cd Kafka-Twitter-Producer                 <--
	mvn clean install                         <-- compile the source
