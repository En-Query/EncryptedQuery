java -cp ../../target/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.responder.wideskies.ResponderDriver \
 -d base -ds ./dataschema.xml  \
 -p kafka -qs ./queryschema.xml -q ./stream-demo-query -o ./stream-demo-result


