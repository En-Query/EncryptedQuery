# *******************
# This script will execute all 3 parts if the encrypted search
# 1st calling generate_query to generate the encrypted query
# 2nd call the responder to generate encrypted resultset
# 3rd decrypt the resultset into plain text
#
#*************************************
#!/bin/bash
echo "Generate query file..."
./generate_query.sh
echo "Run the query against JDBC database..."
./run_responder.sh
echo "Decrypt the result..."
./decrypt_result.sh jdbc-demo-result
echo "Display plain results"
cat plain-result
