# Example using XETRA Data Set

The XETRA data set contains trade data relating to stock market trades dating from Jun-26-2016 onwards. Each row represents an aggregate of one-minute of trade activity for each security, following the Open/High/Low/Close (OHLC) format, with the number of trades and traded contracts also provided.

See [DBG PDS Data Dictionary](https://github.com/Deutsche-Boerse/dbg-pds/blob/master/docs/data_dictionary.md#xetra)

The data is in CSV format, and can be downloaded from Amazon S3 Bucket.  For instructions [Deutsche Börse Public Dataset (DBG PDS)](https://github.com/Deutsche-Boerse/dbg-pds)

### Prepare MariaDB table

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
	
### Populate the MariaDB table with content from the CSV files

Extract the _xetra-csv-files.tar.bz2_ file included in this directory, which contains a subset of the CSV files hosted in the Amazon S3 bucket. There is a total of 1,209,892 records in these files, excluding header rows. As an alternative, you can download these files yourself, just make sure all the CSV files are in the same directory.

Now we will generate a file with _LOAD DATA..._ SQL statements from the list of CSV files in this directory using the provided AWK script.  From the directory containing all the CSV file, issue the following command:

	ls -1 -d  $PWD/*.csv  | awk -f load_csv_file.awk  > load_csv_files.sql
	
This will generate a _load_csv_files.sql_ file containing one statement for each CSV file that will import the file content to the XETRA table.
You can now execute this SQL script to load the CSV data, this process may take some time to run.

At this point, you have loaded the Data into MariaDB.

### Configure Responder Data Schema

The included file _xetra-data-schema.xml_ contains a Data Schema describing the XETRA data set fields.  To deploy this Data Schema you will need access to the server running Responder Server.
Copy this file into the Responder inbox directory (by default _/var/EQResponder/inbox/_)

	cp xetra-data-schema.xml /opt/enquery/dataschemas/inbox/  
	
The responder will automatically injest the new data achema after a minute or so and be made available to Query.  (Note: this step should already have been done during deployment)

### Configure a Data Source using Flink engine on the XETRA MariaDB database table.

We will use the following SQL select as the Query:

	SELECT ISIN, Mnemonic, SecurityDesc, SecurityType,  Currency, SecurityID, 
		DATE_FORMAT(ADDTIME(TradingDate, TradingTime), "%Y-%m-%dT%TZ") AS TadingDate,
         		StartPrice, MaxPrice, MinPrice, EndPrice, TradedVolume, NumberOfTrades
         	FROM XETRA
 
The above query takes care of presenting Trade date in a ISO-8601 format so it can be treated as a Date value by Encrypted Query.  This Query, along with additional configuration, such as the path to Flink, connection to the MariaDB server, etc. is captured by the configuration file _org.enquery.encryptedquery.responder.flink.jdbc.runner.FlinkJdbcQueryRunner-MariaDBXETRA.cfg_ which is included.  You can update this example file to your needs and copy it to Responder's _etc_ directory:

	cp org.enquery.encryptedquery.responder.flink.jdbc.runner.FlinkJdbcQueryRunner-MariaDBXETRA.cfg   /opt/enquery/responder/etc
	
This will activate an additional Data Source that can be used in Querier UI to schedule Queries against the XETRA data set.

### Run the example using the UI.
Connect to the Querier through a browser (Tested with Chrome)  URL: http://<querier IP>:8182/querier/
Select `Create Query Schema` menu option.
Enter a name for the Query Schema. (`QS-Xetra`) Names must be unique!
Select the `XETRA` data schema to use from the drop down list.   If there are no entries in the list there are no data schemas defined yet.  Make sure the data schemas have been injested into the responder.
Select the `Mnemonic` field for the selector.
Check on each field you would like to be returned in the result set.
After you enable each field you need to select the length type ('variable' or 'fixed'), set the max size of the field in bytes and if the field is an array element how many array elements are to be returned.
Since the `Mnemonic` field is the selector it has to be one of the fields selected.
Fields checked for demo:
```
Name, length Type, Size, Max Array Elements
Mnemonic,    variable,    20,      1
currency,    variable,    16,      1
MaxPrice, fixed, 4, 1
MinPrice, fixed, 4, 1
TradingDate, variable, 20, 1
SecurityType, variable, 30, 1
SecurityDesc, variable, 1000, 1
```
Click on the `Submit` button to create the query schema.  
The UI will now take you directly to the Query Encryption page to create a Query.
Enter a name for they Query.  Names must be unique!
Select `XERTA` for the Data Schema
Select `QS-Xerta` for the Query Schema.  Or whatever name was used above
Select the data Chunk size.   Use `3` for this example
Select `True` to embed the selector
Enter `LETC` for a selector value and click the `Add` button
Enter `NESR` for a selector value and click the `Add` button
(Note: case and length matter for matching selector values.)
Click the `Create Query` button to encrypt the query.
The UI will automatically change to the Query Status page.
Select the `XERTA` data schema and `QS-Xerta` query schema.
The query that was just created will show up in the list.  When it has been fully Encrypted the `Action` will show `Schedule`.
Click on the `Schedule` button to schedule the query.
Select the `Flink-JDBC-MariaDB-XERTA` datasource from the drop down list.  If no data sources are available then they have not been configured on the responder yet.
Set the `maxHitsPerSelector` parameter to `1000`
You can schedule this query to run in the future by setting the data/time or just click on 'Submit Query' to schedule the job now.
The UI will change to the `Query Status` page.   You can monitor the job from the Flink Web DashBoard http://<Flink server IP>:8081 
The query may take up to 5 minutes to run depending on the resources available to Flink to execute on.
Select the `XERTA` data Schema and `QS-Xerta` Query schema.
Select `View Schedules` under Action column for the query.
The Query Schedule Status page appears.  If the Query has finished on the responder the Action should say `Get Details`
Click on `Get Details`.  It will then switch you to the Retrievals and Results page.
Click on `Download` to download the results from the responder to the querier.   After ~20 seconds you should see the action change from `Download` to `Decrypt`.   Click on `Decrypt` to decrypt the response.
You can now review the results on the querier server in the `clear-response.xml` file.






