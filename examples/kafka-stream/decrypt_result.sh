ENQUERY_HOME="../.."

java -Djava.library.path=$ENQUERY_HOME/lib/native/ -cp $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar  org.enquery.encryptedquery.querier.wideskies.QuerierDriver -a decrypt -qf stream-demo-querier -i stream-demo-result-1 -nt 4 -o stream-demo-plain-result -qs queryschema.xml -ds dataschema.xml
