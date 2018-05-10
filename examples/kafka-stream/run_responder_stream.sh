#**
# This script will run the Encrypted Query Responder
# to process streaming data from a kafka data stream
# It is assumed that kafka has been installed and a topic for this demo created
#**
#!/bin/bash

ENQUERY_HOME="../.."

java -Djava.library.path=$ENQUERY_HOME/lib/native -cp $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.responder.wideskies.ResponderDriver \
 -d base -ds ./dataschema.xml  \
 -p kafka -qs ./queryschema.xml -q ./stream-demo-query -o ./stream-demo-result


