date
ENQUERY_HOME="../.."
java -cp $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.querier.wideskies.QuerierDriver -a encrypt \
 -b 32 -c 128 -dps 24 -hb 12 -pbs 3072 -i tickers.txt -qt "Business Article Query" -nt 4 \
 -qs queryschema.xml -ds dataschema.xml -m fast -o jdbc-demo  &>> genqry.log
date


