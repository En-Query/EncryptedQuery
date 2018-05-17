echo "Starting Encrypted Query..."
./generate_query.sh
./start_stream.sh
./run_responder_stream.sh
./decrypt_result.sh stream-demo-result-1
./decrypt_result.sh stream-demo-result-2
echo " "
echo "Results from 1st Iteration"
cat stream-demo-result-1-plain-text
echo " "
echo "Results from 2nd Iteration"
cat stream-demo-result-2-plain-text
echo "Streaming finished"

