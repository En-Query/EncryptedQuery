## Example using XETRA Data Set

The XETRA data set contains trade data relating to stock market trades dating from Jun-26-2016 onwards. Each row represents an aggregate of one-minute of trade activity for each security, following the Open/High/Low/Close (OHLC) format, with the number of trades and traded contracts also provided.

See [DBG PDS Data Dictionary](https://github.com/Deutsche-Boerse/dbg-pds/blob/master/docs/data_dictionary.md#xetra)

The data is in CSV format, and can be downloaded from Amazon S3 Bucket.  For instructions [Deutsche Börse Public Dataset (DBG PDS)](https://github.com/Deutsche-Boerse/dbg-pds)

# Prepare MariaDB table

Create a table with the following fields in your MariaDB database:

	CREATE TABLE XETRA (
    		ISIN     VARCHAR(180),
    		Mnemonic VARCHAR(32),
    		SecurityDesc VARCHAR(256),
    		SecurityType VARCHAR(256),
    		Currency CHAR(3),
    		SecurityID int,
    		TradingDate Date,
    		TradingTime Time,
    		StartPrice float,
    		MaxPrice float,
    		MinPrice float,
    		EndPrice float,
    		TradedVolume float,
    		NumberOfTrades int);
	
**Populate the MariaDB table with content from the CSV files**

Extract the _xetra-csv-files.tar.bz2_ file included in this directory, which contains a subset of the CSV files hosted in the Amazon S3 bucket. There is a total of 1,209,892 records in these files, excluding header rows. As an alternative, you can download these files yourself, just make sure all the CSV files are in the same directory.

Now we will generate a file with _LOAD DATA..._ SQL statements from the list of CSV files in this directory using the provided AWK script.  From the directory containing all the CSV file, issue the following command:

	ls -1 -d  $PWD/*.csv  | awk -f load_csv_file.awk  > load_csv_files.sql
	
This will generate a _load_csv_files.sql_ file containing one statement for each CSV file that will import the file content to the XETRA table.
You can now execute this SQL script to load the CSV data, this process may take some time to run.

At this point, you have loaded the Data into MariaDB.

# Configure Responder Data Schema

The included file _xetra-data-schema.xml_ contains a Data Schema describing the XETRA data set fields.  To deploy this Data Schema you will need access to the server running Responder Server.
Copy this file into the Responder inbox directory (by default _/var/EQResponder/inbox/_)

	cp xetra-data-schema.xml /var/EQResponder/inbox/  
	
After a few seconds this Data Schema will be imported by Responder and made available to Query.

# Configure a Data Source using Flink engine on the XETRA MariaDB database table.

We will use the following SQL select as the Query:

	SELECT ISIN, Mnemonic, SecurityDesc, SecurityType,  Currency, SecurityID, 
		DATE_FORMAT(ADDTIME(TradingDate, TradingTime), "%Y-%m-%dT%TZ") AS TadingDate,
         		StartPrice, MaxPrice, MinPrice, EndPrice, TradedVolume, NumberOfTrades
         	FROM XETRA
 
The above query takes care of presenting Trade date in a ISO-8601 format so it can be treated as a Date value by Encrypted Query.  This Query, along with additional configuration, such as the path to Flink, connection to the MariaDB server, etc. is captured by the configuration file _org.enquery.encryptedquery.responder.flink.jdbc.runner.FlinkJdbcQueryRunner-MariaDBXETRA.cfg_ which is included.  You can update this example file to your needs and copy it to Responder's _etc_ directory:

	cp org.enquery.encryptedquery.responder.flink.jdbc.runner.FlinkJdbcQueryRunner-MariaDBXETRA.cfg   /opt/enquery/responder/etc
	
This will activate an additional Data Source that can be used in Querier UI to schedule Queries against the XETRA data set.

This example uses direct REST calls to the querier to show examples of how those messages are defined.

##### Importing the REST calls into postman
Copy the /home/encryptedquery/examples/rest-collections/XETRA-Example-postman-collection.json to where it can be accessed by the Postman app.
Start the Postman app and click on the Import button.  Select the `XETRA-Example-postman-collection.json` file to load.
** Note: The examples in the collection use `192.168.200.58` for the querier server, substitute your servers IP or DNS name to connect to your querier installation.


