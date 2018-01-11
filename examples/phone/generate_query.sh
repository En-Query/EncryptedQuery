# *******************
# This script will deploy the SCMS tar file for given component for Karaf.
# It assumes that the tar file has been copied to the home folder
#
#*************************************
#!/bin/bash

die () {
  echo >&2 "$@"
  echo "Usage: generate_query.sh [name]"
  exit 1
}

[ "$#" -eq 1 ] || die "1 argument required, $# provided"

NAME=$1

ENQUERY_HOME="/opt/enqueryexamples"

java -cp $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.querier.wideskies.QuerierDriver -a encrypt \
 -b 32 -c 128 -dps 16 -hb 15 -pbs 3072 -i $NAME.txt -qt "$NAME query" -nt 4 \
 -qs queryschema_$NAME.xml -ds dataschema_$NAME.xml -m fast -o $NAME  


