This is a JDBC example to test the Encrypted Query.

Pre-Requisites:
Build the EncryptedQuery project
Install MySQL/MariaDB using the default ports
create the enquery database
create the enquery database user with full access to the enquery database.  Password should be test
import the data located in /examples/jdbc/data/xxxx folder into the enquery database under the enquery user.

The above steps can be executed using the .sql files installed in the /data folder.
mysql --user=root --password=<your root password> < /data/create_database.sql     <-- This will create the 'enquery' database and also create the user 'enquery'
mysql --user=enquery --password=enquery enquery < enquery_businessarticles.sql    <-- This will create the 'businessarticles' table in the enquery database and import the sample dataset


Update the encryptedquery.properties to point to the jdbc database 

Execution:
Execute the run_jdbc_query.sh script to perform a complete exercise (Update configuration, Generate Query, Start Streaming, Run Responder, & Decrypt Result)
You can also execute these steps individually by executing:
generate_query.sh               <-- Generate the Query 
run_responder.sh                <-- Run the responder 
decrypt_result.sh <result file> <-- Decrypt the result



