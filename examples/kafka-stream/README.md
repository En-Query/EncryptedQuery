This is a streaming example to test the Encrypted Query in kafka streaming mode.

Pre-Requisites:
Install Kafka/zookeeper using the default ports
create the kafka topic "stream-test"
Build the Encrypted Query Application

Execution:
Execute the run_streaming_example.sh script to perform a complete exercise (Update configuration, Generate Query, Start Streaming, Run Responder, & Decrypt Result)

You can also execute these steps individually by executing:
generate_query.sh               <-- Generate the Query 
start_stream.sh                 <-- Start the kafka producer to stream data 
run_responder_stream.sh         <-- Run the responder 
decrypt_result.sh               <-- Decrypt the result


The source code for the Kafka Producer has been included with this demo.  It is a simple producer that will spawn 'x' number of producer threads
to publish records into a kafka topic.  The records are generated ramdomly with some select selectors added in for the query.

To compile the kafka--producer:
        cd /examples/kafka-stream/
        tar -xvf kafka-producer.tar               <-- Extract from the tar file

    This will install a .jar file to execute.   You can also re-create the jar file by issuing a mvn clean install command
