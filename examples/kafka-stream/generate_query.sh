java -cp ../../target/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.querier.wideskies.QuerierDriver \
-a encrypt -c 128 -dps 8 -hb 12 -pbs 3072  -i phone.txt -qt "Streaming Phone Query" -nt 1 -qs queryschema.xml \
 -ds dataschema.xml -m fast -o stream-demo
