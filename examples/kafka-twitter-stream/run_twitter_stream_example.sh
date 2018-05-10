echo "Starting Encrypted Query..."
echo "..Generating Query..."
./generate_query.sh
echo "..Starting Twitter Stream..."
./start_stream.sh
echo "..Start the responder.."
./run_responder_stream.sh
echo ".. Decrypt the results.."
./decrypt_result.sh stream-demo-result-1
./decrypt_result.sh stream-demo-result-2
echo ".. Display the results.."
cat stream-demo-result-1-plain.txt
cat stream-demo-result-2-plain.txt
echo "Streaming finished, restoring original configuration"

