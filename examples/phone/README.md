This example allows you to run Encrypted Query in either Standalone or Hadoop mapreduce mode
When running in MapReduce mode the schema, data, & query files must be loaded into hadoop before
the responder is run.  The run_responder.sh script shows an example of that.
This has been tested on Hadoop 2.7.x and 3.0.0

Pre-Requisites:
Build the Encrypted Query Application 
Install Hadoop and make sure the account running these scripts has read/write privilages to the hadoop instance.

Execution:
Execute the run_encrypted_query.sh script to perform a complete exercise (Generate Query, Run Responder, Decrypt Result)  
This script requires 2 arguments [query name] and [method]
   * query name   is a name give for this query and used in the filenames/folder names
   * method       is used to determine which mode to execute in (standalone or mapreduce)
 
You can also execute these steps individually by executing:
generate_query.sh [query name]              <-- Generate the Query 
run_responder.sh [query name] [method]      <-- Run the responder
decrypt_result.sh [query name]              <-- Decrypt the result

(i.e.   ./run_enqrypted_query.sh phone mapreduce     <-- This will execute using Hadoop MapReduce functionallity )



