ENQUERY_HOME="../.."
java -cp $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar  org.enquery.encryptedquery.querier.wideskies.QuerierDriver \
  -a decrypt -qf demographic-querier -i demographic-query-result -nt 1 \ 
  -o demographic-plain-result -qs queryschema.xml -ds dataschema.xml
