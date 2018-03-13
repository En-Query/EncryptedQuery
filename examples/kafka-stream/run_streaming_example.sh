echo "Updating configuration..."
jar -xf ../../target/encryptedquery-1.0.0-SNAPSHOT-exe.jar encryptedquery.properties
cp encryptedquery.properties encryptedquery.properties.orig
sed -i "s|/path-to-local-properties-files/|$PWD/config/|g" encryptedquery.properties
jar -uf ../../target/encryptedquery-1.0.0-SNAPSHOT-exe.jar encryptedquery.properties
rm encryptedquery.properties
echo "Starting Encrypted Query..."
./generate_query.sh
./start_stream.sh
./run_responder_stream.sh
./decrypt_result.sh
cat stream-demo-plain-result
echo "Streaming finished, restoring original configuration"
mv encryptedquery.properties.orig encryptedquery.properties
jar -uf ../../target/encryptedquery-1.0.0-SNAPSHOT-exe.jar encryptedquery.properties
rm encryptedquery.properties

