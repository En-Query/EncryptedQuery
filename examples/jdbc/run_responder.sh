date
ENQUERY_HOME="../.."
java -XX:+UseG1GC -Djava.library.path=$ENQUERY_HOME/lib/native/ -cp $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.responder.wideskies.ResponderDriver  \
 -bif org.enquery.encryptedquery.inputformat.hadoop.json.JSONInputFormatBase -d base \
 -ds dataschema.xml -p jdbc \
 -mh 16384 -qs queryschema.xml -q jdbc-demo-query \
 -o jdbc-demo-result &>> resp-jdbc.log

