# *******************
# This script will deploy the SCMS tar file for given component for Karaf.
# It assumes that the tar file has been copied to the home folder
#
#*************************************
#!/bin/bash

die () {
  echo >&2 "$@"
  echo "Usage: generate_query.sh [name] [method (standalone or mapreduce)]"
  exit 1
}

[ "$#" -eq 2 ] || die "2 arguments required, $# provided"

NAME=$1
METHOD=$2

ENQUERY_HOME="../.."

if [ "$METHOD" == "standalone" ]; then
   echo "Running Standalone"
   java -Djava.library.path=$ENQUERY_HOME/lib/native/ -cp $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.responder.wideskies.ResponderDriver \
      -d base -ds ./dataschema_$NAME.xml -i ./datafile_$NAME.json -p $METHOD -qs ./queryschema_$NAME.xml -q ./$NAME-query -o ./$NAME-query-result
else
   echo " Running Hadoop Map/Reduce"
   fn=$NAME-query
   if [ -f $fn ] ; then
     echo "Copying query files to Hadoop.."
     bfn=`basename $fn` #trim path from filename
     hdfs dfs -mkdir -p /user/enquery/$NAME/
     hdfs dfs -mkdir -p /user/enquery/lib/
     hdfs dfs -put -f $fn /user/enquery/$NAME/$bfn
     hdfs dfs -put -f ./dataschema_$NAME.xml /user/enquery/$NAME/
     hdfs dfs -put -f ./queryschema_$NAME.xml /user/enquery/$NAME/
     hdfs dfs -put -f ./datafile_$NAME.json /user/enquery/$NAME/
     hdfs dfs -put -f $ENQUERY_HOME/lib/native/libresponder.so /user/enquery/lib/
     hdfs dfs -ls /user/enquery/$NAME/$bfn
     success=$? #check whether file landed in hdfs
     if [ $success ] ; then
       echo "Successfully copied query files for $fn into Hadoop"
     fi
   fi

   hadoop jar $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.responder.wideskies.ResponderDriver \
       -bif org.enquery.encryptedquery.inputformat.hadoop.json.JSONInputFormatBase \
       -d base -ds /user/enquery/$NAME/dataschema_$NAME.xml -i /user/enquery/$NAME/datafile_$NAME.json -p $METHOD \
       -mh 16384 -qs /user/enquery/$NAME/queryschema_$NAME.xml -q /user/enquery/$NAME/$NAME-query -nr 10 -o /user/enquery/$NAME/$NAME-query-result

   echo "Responder complete, copying encrypted result.."
   rm -rf $NAME-query-result
   hdfs dfs -get  /user/enquery/$NAME/$NAME-query-result
   success=$? #check whether result set was copied from hdfs
   if [ $success ] ; then
      echo "Successfully copied Result Set to local folder"
   fi

fi

