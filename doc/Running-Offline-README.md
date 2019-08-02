# Running Encrypted Query in Offline Mode
In offline mode, there is no direct network connection between Querier and Responder. All interaction
is accomplished by means of imported and exported files between the servers. On the Querier side, Import and Export of files can be done either using the Web interface, or console commands. On the Responder side, all files are imported/exported via console commands.

### Prerequisites 
* Querier and Responder have been installed and are running.
* Querier has been set to offline mode.
* Flash drive or other method to transfer files between the Querier and Responder.

#### Setting the Querier to Offline Mode
To set the querier to Offline Mode, edit the `querier/etc/encrypted.query.querier.rest.cfg` and add (or modify) the following line:

```
responder.offline=true
```

Where `querier` is the directory where Querier is installed.
Alternatively, configuration settings can be changed from within the Querier console. To access the Querier
console, follow the following steps:

```
$querier/bin/client

     EncryptedQuery Querier (2.2.0) 

Hit '<tab>' for a list of available commands
and '[cmd] --help' for help on a specific command.
Hit '<ctrl-d>' or 'osgi:shutdown' to shutdown Karaf.

karaf@root()>
```
Once you are in the console, you can then issue the following commands:

```
karaf@root()> config:edit encrypted.query.querier.rest 
karaf@root()> config:property-set responder.offline true
karaf@root()> config:update 
karaf@root()> logout
```

The `logout` command exits the console. There is no need to restart the server after making configuration changes.

 
###### Summary of Responder Commands
 * `dataschema --help`   ---     show a listing of all dataschema commands.
 * `dataschema:list`     ---     listing of all data schemas
 * `dataschema:export ` --- export all data schemas to the specified directory
 * `datasource --help` ---   show a listing of all datasource commands
 * `datasource:list`  ---  show a listing of all registered data sources
 * `datasource:export ` ---  export all data sources and data schemas to the specified difectory

###### Summary of Querier Commands 
 * `datasource:list`  ---   show a listing of all datasources in the querier
 * `datasource:import` --- import all datasources and dataschemas into the querier
 * `result:import` ---  import results file into the querier
 * `schedule:list` ---  show a listing of all schedules
 * `schedule:export` --- export schedules

##### Exporting Data Schemas and Data Sources
To access Responder console:

```
$responder/bin/client
	
EncryptedQuery Responder (2.2.0) 

Hit '<tab>' for a list of available commands
and '[cmd] --help' for help on a specific command.
Hit '<ctrl-d>' or 'osgi:shutdown' to shutdown Karaf.

karaf@root()>
```

Where `responder` is the directory where Responder is installed.  The `logout` command exits the console.

To show available Data Sources:

```
karaf@root()> datasource:list
Id │ Name                            │ Type      │ Description                     │ Data Schema
───┼─────────────────────────────────┼───────────┼─────────────────────────────────┼──────────────────
 5 │ Standalone-Phone-Data-10M       │ Batch     │ Standalone Engine running on a  │ Phone Data
 4 │ Standalone-Phone-Data-100M      │ Batch     │ Standalone Engine running on a  │ Phone Data
 3 │ Standalone-Phone-Data-5         │ Batch     │ Standalone Engine running on a  │ Phone Data
10 │ Hadoop-MR-Phone-Data-1M         │ Batch     │ Hadoop Map Reduce Engine runnin │ Phone Data
14 │ Hadoop-MR-Phone-Data-5K         │ Batch     │ Hadoop Map Reduce Engine runnin │ Phone Data
12 │ Hadoop-MR-Phone-Data-1M-v1      │ Batch     │ Hadoop MR 1M record Hadoop data │ Phone Data
11 │ Hadoop-MR-Phone-Data-10M        │ Batch     │ Hadoop Map Reduce Engine runnin │ Phone Data
13 │ Hadoop-MR-Phone-Data-100M       │ Batch     │ Hadoop Map Reduce Engine runnin │ Phone Data
 6 │ Flink-Kafka-Pcap                │ Streaming │ Flink Engine running on a Kafka │ Pcap
 9 │ Flink-JDBC-MariaDB-XETRA        │ Batch     │ Flink Engine running on a Maria │ XETRA
 7 │ Flink-JDBC-MariaDB-Phone-Record │ Batch     │ Flink Engine running on a Maria │ Phone Record
 8 │ Flink-JDBC-MariaDB-Business-Art │ Batch     │ Flink Engine running on a Maria │ Business Articles
 1 │ Standalone-Phone-Data-1M        │ Batch     │ Standalone Engine running on a  │ Phone Data
 2 │ Standalone-Phone-Data-5K        │ Batch     │ Standalone Engine running on a  │ Phone Data
```

To export available Data Sources (and their associated Data Schemas):

```
karaf@root()> datasource:export  --help
DESCRIPTION
        datasource:export

	Export Data Sources and Data Schemas in XML format for importing into Querier.

SYNTAX
        datasource:export [options]

OPTIONS
        --help
                Display this help message
        --output-dir, -o, Output directory.
                Output directory to write the export file to.
                (required)
```

Example:

```
karaf@root()> datasource:export --output-dir /opt/enquery/
14 Data Sources exported to '/opt/enquery/export-datasources-20190416T014848.xml'.
```

A timestamp is appended to the created file name.  This file includes all Data Sources and their corresponding Data Schemas availabale in Responder.  The next step is to import this file into Querier.

##### Importing the Data Schemas and Data Sources into the Querier
###### Using the console
To import Data Sources and Data Schemas into Querier using the console, copy the exported file `/opt/enquery/export-datasources-<DateStamp>.xml` to the querier server:

```
karaf@root()>datasource:import --input-file /opt/enquery/export-datasources-20190416T071657.xml
Successfully imported '/opt/enquery/export-datasources-20190416T071657.xml'
```

To verify they are properly loaded:

```
karaf@root()> datasource:list
Id │ Name                            │ Type      │ Description                     │ Data Schema       │ Responder URI                                    │ Executions URI
───┼─────────────────────────────────┼───────────┼─────────────────────────────────┼───────────────────┼──────────────────────────────────────────────────┼────────────────────────────────────────────────────────────
 1 │ Standalone-Phone-Data-1M        │ Batch     │ Standalone Engine running on a  │ Phone Data        │ /responder/api/rest/dataschemas/5/datasources/1  │ /responder/api/rest/dataschemas/5/datasources/1/executions
 2 │ Standalone-Phone-Data-5K        │ Batch     │ Standalone Engine running on a  │ Phone Data        │ /responder/api/rest/dataschemas/5/datasources/2  │ /responder/api/rest/dataschemas/5/datasources/2/executions
 3 │ Standalone-Phone-Data-5         │ Batch     │ Standalone Engine running on a  │ Phone Data        │ /responder/api/rest/dataschemas/5/datasources/3  │ /responder/api/rest/dataschemas/5/datasources/3/executions
 4 │ Standalone-Phone-Data-100M      │ Batch     │ Standalone Engine running on a  │ Phone Data        │ /responder/api/rest/dataschemas/5/datasources/4  │ /responder/api/rest/dataschemas/5/datasources/4/executions
 5 │ Standalone-Phone-Data-10M       │ Batch     │ Standalone Engine running on a  │ Phone Data        │ /responder/api/rest/dataschemas/5/datasources/5  │ /responder/api/rest/dataschemas/5/datasources/5/executions
 6 │ Flink-Kafka-Pcap                │ Streaming │ Flink Engine running on a Kafka │ Pcap              │ /responder/api/rest/dataschemas/3/datasources/6  │ /responder/api/rest/dataschemas/3/datasources/6/executions
 7 │ Flink-JDBC-MariaDB-Phone-Record │ Batch     │ Flink Engine running on a Maria │ Phone Record      │ /responder/api/rest/dataschemas/4/datasources/7  │ /responder/api/rest/dataschemas/4/datasources/7/executions
 8 │ Flink-JDBC-MariaDB-Business-Art │ Batch     │ Flink Engine running on a Maria │ Business Articles │ /responder/api/rest/dataschemas/2/datasources/8  │ /responder/api/rest/dataschemas/2/datasources/8/executions
 9 │ Flink-JDBC-MariaDB-XETRA        │ Batch     │ Flink Engine running on a Maria │ XETRA             │ /responder/api/rest/dataschemas/8/datasources/9  │ /responder/api/rest/dataschemas/8/datasources/9/executions
10 │ Hadoop-MR-Phone-Data-1M         │ Batch     │ Hadoop Map Reduce Engine runnin │ Phone Data        │ /responder/api/rest/dataschemas/5/datasources/10 │ /responder/api/rest/dataschemas/5/datasources/10/executions
11 │ Hadoop-MR-Phone-Data-10M        │ Batch     │ Hadoop Map Reduce Engine runnin │ Phone Data        │ /responder/api/rest/dataschemas/5/datasources/11 │ /responder/api/rest/dataschemas/5/datasources/11/executions
12 │ Hadoop-MR-Phone-Data-1M-v1      │ Batch     │ Hadoop MR 1M record Hadoop data │ Phone Data        │ /responder/api/rest/dataschemas/5/datasources/12 │ /responder/api/rest/dataschemas/5/datasources/12/executions
13 │ Hadoop-MR-Phone-Data-100M       │ Batch     │ Hadoop Map Reduce Engine runnin │ Phone Data        │ /responder/api/rest/dataschemas/5/datasources/13 │ /responder/api/rest/dataschemas/5/datasources/13/executions
14 │ Hadoop-MR-Phone-Data-5K         │ Batch     │ Hadoop Map Reduce Engine runnin │ Phone Data        │ /responder/api/rest/dataschemas/5/datasources/14 │ /responder/api/rest/dataschemas/5/datasources/14/executions
```

You can also view the Data Schemas:

```
karaf@root()> dataschema:list
Id │ Name              │ Number of fields
───┼───────────────────┼─────────────────
 1 │ Book Catalog      │ 5
 2 │ Business Articles │ 6
 3 │ Pcap              │ 16
 4 │ Phone Data        │ 4
 5 │ Phone Record      │ 6
 6 │ Simple Data       │ 3
 7 │ Twitter           │ 12
 8 │ XETRA             │ 13
```

###### Using the web interface:

* Copy the `export-datasources-<DateStamp>.xml` file from the responder server to a location that can be accessed by your web browser. 
* Click "Offline Mode" on the upper right corner
* Click "Import DataSource" tab
* Click "File Input & Upload".  A file chooser window will pop-up to allow you to select the `export-datasources-<DateStamp>.xml` file
* Click "Upload" 

You should see a "File Upload Success" status message appear at the bottom.

#### Create the Query Schemas and Encrypted Query
Data Schemas and Data Sources have now been loaded in Querier server.   You can now use the web interface, (or REST interface) to create the Query Schemas and Encrypt queries to search with.

##### Scheduling a job offline
Once you have an Encrypted Query use the query status page to schedule the job.  For Offline Mode, click on the "Save for Export."  You can schedule multiple jobs and save them all for offline execution.   Once the scheduled jobs have been loaded in Responder, they will execute serially.

Note: You can also schedule the job using the REST interface (not covered here).

##### Exporting the Saved Jobs from the Querier
###### Using the web interface

* Navigage to the 'Offline Page' then click on the 'Exports Schedules' tab.
* Select the Data Schema, Query Schema, and Query used to schedule the Job.  A list of Scheduled Jobs will appear in the table below.
* Select the jobs to be exported by Checking the 'Export Box'.
* Click on the 'Export All' button. Depending on your browser, you may get a file save prompt, or you will see the file download start into the default downloads folder.

###### Using the console

List available schedules:

```
karaf@root()> schedule:list
Id │ UUID                             │ Status   │       Data Source        │ Start time       │ Query                 │ Error
───┼──────────────────────────────────┼──────────┼──────────────────────────┼──────────────────┼───────────────────────┼────────────────────────────────
 1 │ d13aab38193d44038d5ee24cfdcbcf6d │ Complete │ Standalone-Phone-Data-1M │ 4/16/19 7:22 AM  │ Q-Phone-Data          │
 2 │ 468f06a8d0354e41821b7f2b6d8d31ab │ Complete │ Hadoop-MR-Phone-Data-1M  │ 4/16/19 9:16 AM  │ Q-Phone-Data          │
 3 │ d10b38ef099b438bbdf5399778b67b73 │ Complete │ Standalone-Phone-Data-1M │ 4/16/19 9:41 AM  │ Q-Phone-Data          │
 4 │ 0624d5d05b9346119322919af2957c7a │ Complete │ Standalone-Phone-Data-1M │ 4/16/19 9:46 AM  │ Q-Phone-Data          │
 5 │ 558444540b704da9af38f3b89a21f03e │ Failed   │ Standalone-Phone-Data-5K │ 4/16/19 11:03 AM │ Q-Phone-Data          │ Standalone Query Runner exited
 6 │ 8bed5bc8cbf64b3ebcc5466768a2c14e │ Complete │ Standalone-Phone-Data-1M │ 4/16/19 11:04 AM │ Q-Phone-Data          │
 7 │ 16df79b27f3d43769e4bc24ad78324ff │ Pending  │ Standalone-Phone-Data-1M │ 4/16/19 11:07 AM │ Q-Phone-Data          │
 8 │ 0f185313ed0947ee813f585e68a21090 │ Pending  │ Standalone-Phone-Data-1M │ 4/16/19 11:32 AM │ Q-Phone-Data-wes      │
 9 │ 99fb3a1cdcdf41a48983db58f3516d9b │ Pending  │ Standalone-Phone-Data-1M │ 4/16/19 11:35 AM │ Q-Phone-Data-wes-hb18 │
10 │ aad096dd6d9a47dd9211bd7fe0321eef │ Pending  │ Standalone-Phone-Data-1M │ 4/16/19 11:42 AM │ Q-Phone-Data-wes-hb16 │
11 │ 454cf7db29f64127b15bfebe0d010046 │ Pending  │ Standalone-Phone-Data-5K │ 4/16/19 11:44 AM │ Q-Phone-Data-wes-hb17 │
12 │ cb8f16d632d348be9e4372cb464efcc9 │ Failed   │ Standalone-Phone-Data-5K │ 4/16/19 12:34 PM │ Q-Phone-Data-wes-hb16 │ Standalone Query Runner exited
13 │ 44d05e9100bc4cd38f4b782f094a63a4 │ Failed   │ Standalone-Phone-Data-5K │ 4/16/19 1:11 PM  │ Q-Phone-Data-wes-hb16 │ Standalone Query Runner exited
14 │ c31262ad6fac4ef6b882c9b6955f9cf5 │ Failed   │ Standalone-Phone-Data-1M │ 4/16/19 1:12 PM  │ Q-Phone-Data-wes-hb16 │ Standalone Query Runner exited
15 │ 522a695cde514126838a0a60aa8fb0eb │ Failed   │ Standalone-Phone-Data-5K │ 4/16/19 1:17 PM  │ Q-Phone-Data-wes-hb16 │ Standalone Query Runner exited
16 │ 58ed3b2c9a74459d8fe178fb447c05ac │ Failed   │ Standalone-Phone-Data-5K │ 4/16/19 1:23 PM  │ Q-Phone-Data-wes-hb16 │ Standalone Query Runner exited
17 │ 951cfebd391e47c9bbc68c96998d1bcb │ Complete │ Standalone-Phone-Data-5K │ 4/16/19 1:25 PM  │ Q-Phone-Data-wes-hb16 │
18 │ 675323a4f3034b098d353b4daf4499c7 │ Complete │ Standalone-Phone-Data-1M │ 4/16/19 1:26 PM  │ Q-Phone-Data-wes-hb16 │
19 │ a63b38dd54444886844d20a1b74f1232 │ Complete │ Hadoop-MR-Phone-Data-1M  │ 4/16/19 1:41 PM  │ Q-Phone-Data-wes-hb17 │
```

From the above list, we will export schedules 8, and 11. 

Display usage information of the export command

```
karaf@root()> schedule:export --help
DESCRIPTION
        schedule:export

	Export schedules in XML format to be imported by Responder.

SYNTAX
        schedule:export [options] schedule ids 

ARGUMENTS
        schedule ids
                Ids of schedules to export.
                (required, multi-valued)

OPTIONS
        --output-file, -o, Output file.
                Output file to write the results to.
                (required)
        --help
                Display this help message
```

Now export schedules 8 and 11:

```
karaf@root()> schedule:export --output-file /opt/enquery/schedule-export.xml 8 11
1 schedules exported to '/opt/enquery/schedule-export.xml'.
```

Multiple schedule ids can be specified separated by space.  You can use <tab> to select available schedule ids instead of typing them.  

Copy the generated file `schedule-export.xml` to a directory in the Responder server.

##### Importing the Schedules to run on the Responder

Using Responder console commands, import the scheduled jobs. Any jobs that have a schedule time in the past will be executed immediatly. If there are more than one job then the jobs will be run serially.

```
karaf@root()> execution:import --input-file /opt/enquery/schedule-export.xml
1 execution added.
Response file '/opt/enquery/add-executions-20190416T091935.xml' created.
```

This command may take some time to load the schedules.  It will provide a response file with the particulars of the scheduled job imported into the responder.  Keep this response file around to use it when obtaining the results of the executions. 

You can view current schedules and their status:

```
karaf@root()> execution:list
Id │ UUID                             │       Data Source        │ Scheduled       │ Started         │ Completed       │ Canceled │ Error
───┼──────────────────────────────────┼──────────────────────────┼─────────────────┼─────────────────┼─────────────────┼──────────┼──────
 1 │ d13aab38193d44038d5ee24cfdcbcf6d │ Standalone-Phone-Data-1M │ 4/16/19 7:22 AM │ 4/16/19 8:30 AM │ 4/16/19 8:31 AM │ false    │
 2 │ 468f06a8d0354e41821b7f2b6d8d31ab │ Hadoop-MR-Phone-Data-1M  │ 4/16/19 9:16 AM │ 4/16/19 9:19 AM │ 4/16/19 9:21 AM │ false    │
```

##### Exporting Results from the Responder

Once the scheduled job has completed, the execution results need to be exported.  Using the response file created during the import of the schedules: 

```
karaf@root()> result:export --input-file /opt/enquery/add-executions-20190416T091935.xml --output-file /opt/enquery/hadoop-result.xml
1 results exported to '/opt/enquery/hadoop-result.xml'.
```

Copy the `hadoop-result.xml` file to the Querier server for import and decryption.

##### Import the Result file into the Querier

###### Using the console

To use the Querier console to import the result file.

```
karaf@root()> result:import --input-file /opt/enquery/hadoop-result.xml
1 results imported.
```

###### Using the web interface
To use the Querier web interface to import the result file.

* Click "Offline Mode" on the upper right corner
* Click "Import Results"
* Click "Choose a File" under "Result File Upload" and select the file containing the execution results. 
* Click "Upload" 


##### Decrypt the Results
Use the Querier web interface, or the REST interface (not covered here) to decrypt the results.
