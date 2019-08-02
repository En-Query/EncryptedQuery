# Running The Hadoop Encrypted Query Example

The hadoop query example searches through a list of phone records searching for the rows whos caller # matches the selector values given.  This example is identical to the Standalone example except the search file is located in Hadoop.  When you get to Step 8 (Schedule a query, use the Hadoop Data source id instead of the Standalone one)

(Note: You an also search multiple files in a hdfs folder by using the folder name as the source file.  All files must have the same data structure)

### Prerequisites
* Encrypted Query querier and responder installed and running.
* Working folder created in HDFS
* Permissions set in HDFS for access to working folder and data file
* Example data file loaded into HDFS (Instructions for this are in the Deployment-README.md)

#### Refer to the instructions in the Standalone-Example-README.md file, substituting the Hadoop data source for the Standalone data source in Step 8.
You should receive the same result set as with the Standalone example.
