ENQUERY_HOME="../.."

java -Djava.library.path=$ENQUERY_HOME/lib/native/ -cp $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar  org.enquery.encryptedquery.querier.wideskies.QuerierDriver -a decrypt -qf stream-demo-querier -i $1 -nt 4 -o $1-plain.txt -qs queryschema.xml -ds dataschema.xml
