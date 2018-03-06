This is a streaming example to test the Encrypted Query in kafka streaming mode.

Pre-Requisites:
Install Kafka/zookeeper using the default ports
create the kafka topic "stream-test"
Generate the encrypted query jar file
Update the encryptedquery.properties to point to the local configuration folder 
Update the shell scripts to point to the jar file.

Execution:
Execute the run_streaming_example.sh script to perform a complete exercise (Update configuration, Generate Query, Start Streaming, Run Responder, & Decrypt Result)
You can also execute these steps individually by executing:
generate_query.sh               <-- Generate the Query 
start_stream.sh                 <-- Start the kafka producer to stream data 
run_responder_stream.sh         <-- Run the responder 
decrypt_result.sh               <-- Decrypt the result



