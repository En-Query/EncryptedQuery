# *******************
# This script will execute all 3 parts if the encrypted search
# 1st calling generate_query to generate the encrypted query
# 2nd call the responder to generate encrypted resultset
# 3rd decrypt the resultset into plain text
#
#*************************************
#!/bin/bash

die () {
  echo >&2 "$@"
  echo "Usage: run_encrypted_query.sh [name] [method (standalone or mapreduce)]"
  exit 1
}

[ "$#" -eq 2 ] || die "2 arguments required, $# provided"

NAME=$1
METHOD=$2

./generate_query.sh $NAME
./run_responder.sh $NAME $METHOD
./decrypt_result.sh $NAME
cat $NAME-plain-result
