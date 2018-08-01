date
ENQUERY_HOME="../.."
java -cp $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.querier.wideskies.QuerierDriver -a decrypt \
 -qf jdbc-demo-querier -i $1 -nt 1 -o plain-result -qs queryschema.xml \
 -ds dataschema.xml &>>decrypt.log
date
