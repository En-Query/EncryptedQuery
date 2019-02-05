# Running The Standalone Encrypted Query Example

The standalone query example searches through a list of phone records searching for the rows whos caller # matches the selector values given.

### Prerequisites
* Encrypted Query querier and responder installed and running.
* [Postman App](https://www.getpostman.com/apps)

This example uses direct REST calls to the querier to show examples of how those messages are defined.

##### Importing the REST calls into postman
Copy the /home/EncryptedQuery/examples/rest-collections/standalone-phone-data_postman_collection.json to where it can be accessed by the Postman app.
Start the Postman app and click on the Import button.  Select the `querier-postman-collection.json` file to load.
** Note: The examples in the collection use `enquery.querier.com` for the querier server, substitute your servers IP or DNS name to connect to your querier installation.

###### Run the example
Step 1) Select the `GET DataSchemas` request and SEND.  This is a GET request which will list the available Data Schemas the responder has access too.   
```
http://enquery.querier.com:8182/querier/api/rest/dataschemas
```
```
{
    "data": [
            {
            "id": "2147483599",
            "selfUri": "/querier/api/rest/dataschemas/2147483599",
            "type": "DataSchema",
            "name": "Pcap"
        },
        {
            "id": "2147483600",
            "selfUri": "/querier/api/rest/dataschemas/2147483600",
            "type": "DataSchema",
            "name": "Business Articles"
        },
        {
            "id": "2147483602",
            "selfUri": "/querier/api/rest/dataschemas/2147483602",
            "type": "DataSchema",
            "name": "Phone Data"
        },
        {
            "id": "2147483604",
            "selfUri": "/querier/api/rest/dataschemas/2147483604",
            "type": "DataSchema",
            "name": "XETRA"
        }
    ]
}
```
Step 2) Select the `GET Data Schema Detail`.  Update the URL and add the `dataschema id` to the end then SEND.  This will return the details of the data schema showing the fields available for query.
```
http://enquery.querier.com:8182/querier/api/rest/dataschemas/2147483602
```
```
{
    "data": {
        "id": "2147483602",
        "selfUri": "/querier/api/rest/dataschemas/2147483602",
        "type": "DataSchema",
        "name": "Phone Data",
        "dataSourcesUri": "/querier/api/rest/dataschemas/2147483602/datasources",
        "querySchemasUri": "/querier/api/rest/dataschemas/2147483602/queryschemas",
        "fields": [
            {
                "name": "Callee #",
                "dataType": "string",
                "isArray": false,
                "position": 3
            },
            {
                "name": "Caller #",
                "dataType": "string",
                "isArray": false,
                "position": 2
            },
            {
                "name": "Date/Time",
                "dataType": "string",
                "isArray": false,
                "position": 1
            },
            {
                "name": "Duration",
                "dataType": "string",
                "isArray": false,
                "position": 0
            }
        ]
    }
}
```
Step 3) Create a Query Schema.  Select the `POST Create QuerySchema` call.  Update the id after `/dataschemas` with the data schema id to be used.  With this post request the Body defines which field will be the selector field and which fields to be returned from the query.  
 * Enter the name for the Query Schema. (Names must be unique)
 * Enter the Selector Field.  This must match the name of a field in the Data Schema.
 * The lengthType field for `string` or `bytearray` fields can be either ("fixed" or "variable").  If set to "variable" the size will be the maximum number of characters/bytes returned for that field.
 * The size field determines the number of bytes that will be returned for that field.
 *  The maxArrayElements sets the number of array elements to be returned if the field is an array.
```
POST http://192.168.200.58:8182/querier/api/rest/dataschemas/2147483602/queryschemas
```
```
"data": {
        "id": "2147483602",
        "selfUri": "/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602",
        "type": "QuerySchema",
        "name": "QS-Phone-Data",
        "selectorField": "Caller #",
        "fields": [
            {
                "name": "Caller #",
                "lengthType": "variable",
                "size": 20,
                "maxArrayElements": 1
            },
            {
                "name": "Callee #",
                "lengthType": "variable",
                "size": 16,
                "maxArrayElements": 1
            },
            {
                "name": "Duration",
                "lengthType": "variable",
                "size": 10,
                "maxArrayElements": 1
            },
            {
                "name": "Date/Time",
                "lengthType": "variable",
                "size": 30,
                "maxArrayElements": 1
            }
        ],
        "dataSchema": {
            "id": "2147483602",
            "selfUri": "/querier/api/rest/dataschemas/2147483602",
            "type": "DataSchema"
        },
        "queriesUri": "/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries"
    },
    "included": [
        {
            "id": "2147483602",
            "selfUri": "/querier/api/rest/dataschemas/2147483602",
            "type": "DataSchema",
            "name": "Phone Data",
            "dataSourcesUri": "/querier/api/rest/dataschemas/2147483602/datasources",
            "querySchemasUri": "/querier/api/rest/dataschemas/2147483602/queryschemas",
            "fields": [
                {
                    "name": "Callee #",
                    "dataType": "string",
                    "isArray": false,
                    "position": 3
                },
                {
                    "name": "Caller #",
                    "dataType": "string",
                    "isArray": false,
                    "position": 2
                },
                {
                    "name": "Date/Time",
                    "dataType": "string",
                    "isArray": false,
                    "position": 1
                },
                {
                    "name": "Duration",
                    "dataType": "string",
                    "isArray": false,
                    "position": 0
                }
            ]
        }
    ]
}
```
Step 4) (Optional) Show the details of a Query Schema.  Select `GET QuerySchema Detail` and update the URL with the data and query schema id's.
```
GET http://enquery.querier.com:8182/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602
```
Step 5) Encrypte the Query.  Select `Encrypt Query` and update the URL with the data and query schema id's.  With this Post request the Body to defines the parameters used to encrypt the query.
 * Query name.  (This must be unique)
 * dataChunkSize  This determines how many bytes to join together for each partition element.  dataChunkSize defaults to 1
 * Add the values this query is to search for.
 * embedSelector.  Set to true.
 ```
 POST http://enquery.querier.com:8182/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries
 ```
 Response:
 ```
 {
    "data": {
        "id": "2147483604",
        "selfUri": "/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604",
        "type": "Query",
        "name": "Q-Phone-Data",
        "status": "Created",
        "querySchema": {
            "id": "2147483602",
            "selfUri": "/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602",
            "type": "QuerySchema"
        },
        "schedulesUri": "/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604/schedules",
        "parameters": {
            "dataChunkSize": "3"
        },
        "selectorValues": [
            "649-186-8058",
            "275-913-7889",
            "930-429-6348",
            "981-485-4656",
            "797-844-8761",
            "893-324-3654",
            "610-968-2867",
            "445-709-2345",
            "410-409-9985"
        ],
        "embedSelector": true
    },
    "included": [
  ...
```
Note: Not all of the post response is shown above.

Step 6) (Optional) Get Query Status.  Select `GET Query Status` and update the URL with the data and query schema id's as well as the query id.
```
GET http://enquery.querier.com:8182/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604
```
In the response the status field will show `Encrypted` when the query has been encrypted and is ready to schedule.


Step 7) List the Data Sources available for the data schema.  Select `Get DataSources for DataSchema` and update the URL with the data schema id.
```
GET http://enquery.querier.com:8182/querier/api/rest/dataschemas/2147483602/datasources
```
```
{
    "data": [
        {
            "id": "2147483603",
            "selfUri": "/querier/api/rest/dataschemas/2147483602/datasources/2147483603",
            "type": "DataSource",
            "name": "Standalone-Phone-Data-5K"
        }
    ]
}
```

Step 8) Schedule the Query.  Select the `Schedule Query` request and update the URL with the appropriate id's.  
Update the body with the following:
 * Date/time to start the query. (Note:  Use UTC time !!) 
 * maxHitsPerSelector  Limit the number of hits any one selector value can have.  The larger the value the more data that will be processed.  Recommended value 1000.
 * data source id.   Enter the Data Source Id you want to query against.
 ```
 POST http://enquery.querier.com:8182/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604/schedules
 ```
 ```
 {
    "data": {
        "id": "2147483605",
        "selfUri": "/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604/schedules/2147483605",
        "type": "Schedule",
        "startTime": 1542809759000,
        "status": "Pending",
        "parameters": {
            "maxHitsPerSelector": "2500"
        },
        "resultsUri": "/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604/schedules/2147483605/results",
        "query": {
            "id": "2147483604",
            "selfUri": "/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604",
            "type": "Query"
        },
        "dataSource": {
            "id": "2147483603",
            "selfUri": "/querier/api/rest/dataschemas/2147483602/datasources/2147483603",
            "type": "DataSource"
        }
    },
    "included": [
  ...
```
Note: Not all of the post response is shown above.

Step 9) Get the Status of the Query.  Select `GET Query Status` from the requests and update the URL with the appropriate id's
```
GET http://enquery.querier.com:8182/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604/schedules/2147483605/results
```
```
{
    "data": [
        {
            "id": "2147483606",
            "selfUri": "/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604/schedules/2147483605/results/2147483606",
            "type": "Result",
            "status": "Ready"
        }
    ]
}
```
Step 10) Retrieve the Results from the Responder to the Querier.  Select `POST Retrieve Results` request and update the URL with the appropriate id's.
```
POST http://enquery.querier.com:8182/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604/schedules/2147483605/results/2147483606/retrievals
```
Note: There is no Body associated with this post request.
```
{
    "data": {
        "id": "2147483607",
        "selfUri": "/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604/schedules/2147483605/results/2147483606/retrievals/2147483607",
        "type": "Retrieval",
        "status": "InProgress",
        "result": {
            "id": "2147483606",
            "selfUri": "/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604/schedules/2147483605/results/2147483606",
            "type": "Result"
        },
        "decryptionsUri": "/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604/schedules/2147483605/results/2147483606/retrievals/2147483607/decryptions"
    },
    "included": [
 ...
```
Note: Not all of the post response is shown above.

Step 11) Decrypt the Query Results.  Select the `POST Decrypt Query Results` request and update the URL with the appropriate id's.
```
POST http://enquery.querier.com:8182/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604/schedules/2147483605/results/2147483606/retrievals/2147483607/decryptions
```
```
{
    "data": {
        "id": "2147483608",
        "selfUri": "/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604/schedules/2147483605/results/2147483606/retrievals/2147483607/decryptions/2147483608",
        "type": "Decryption",
        "status": "InProgress",
        "retrieval": {
            "id": "2147483607",
            "selfUri": "/querier/api/rest/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604/schedules/2147483605/results/2147483606/retrievals/2147483607",
            "type": "Retrieval"
        }
    },
    "included": [
    ....
```
Note: Not all of the post response is shown above.

Step 12 ) Review the Results.  The results will be in the `clear-response.xml` file on the Querier server in the decryptions folder:
```
/opt/enquery/querier/data/blob-storage/dataschemas/2147483602/queryschemas/2147483602/queries/2147483604/schedules/2147483605/results/2147483606/retrievals/2147483607/decryptions/2147483608
```
Note: You will have to update the directory path above with he appropriate id's.
Sample clear-response.xml file
```
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns5:clearTextResponse xmlns="http://enquery.net/encryptedquery/querykey" xmlns:ns2="http://enquery.net/encryptedquery/queryschema" xmlns:ns3="http://enquery.net/encryptedquery/dataschema" xmlns:ns4="http://enquery.net/encryptedquery/query" xmlns:ns5="http://enquery.net/encryptedquery/clear-response" xmlns:ns6="http://enquery.net/encryptedquery/resource" xmlns:ns7="http://enquery.net/encryptedquery/datasource" xmlns:ns8="http://enquery.net/encryptedquery/execution" xmlns:ns9="http://enquery.net/encryptedquery/result" xmlns:ns10="http://enquery.net/encryptedquery/response" xmlns:ns11="http://enquery.net/encryptedquery/pagination">
    <ns5:queryName>QS-Phone-Data</ns5:queryName>
    <ns5:queryId>cf304f46-1a80-4ce9-ad17-fccfa8e81e4a</ns5:queryId>
    <ns5:selector selectorName="Caller #">
        <ns5:hits selectorValue="445-709-2345"/>
        <ns5:hits selectorValue="275-913-7889">
            <ns5:hit>
                <ns5:field>
                    <ns5:name>Callee #</ns5:name>
                    <ns5:value>385-145-4822</ns5:value>
                </ns5:field>
                <ns5:field>
                    <ns5:name>Caller #</ns5:name>
                    <ns5:value>275-913-7889</ns5:value>
                </ns5:field>
                <ns5:field>
                    <ns5:name>Date/Time</ns5:name>
                    <ns5:value>03/19/2016 06:35 PM</ns5:value>
                </ns5:field>
                <ns5:field>
                    <ns5:name>Duration</ns5:name>
                    <ns5:value>01:56:18</ns5:value>
                </ns5:field>
            </ns5:hit>
        </ns5:hits>
        <ns5:hits selectorValue="649-186-8058">
            <ns5:hit>
                <ns5:field>
                    <ns5:name>Callee #</ns5:name>
                    <ns5:value>146-727-7104</ns5:value>
                </ns5:field>
                <ns5:field>
                    <ns5:name>Caller #</ns5:name>
                    <ns5:value>649-186-8058</ns5:value>
                </ns5:field>
                <ns5:field>
                    <ns5:name>Date/Time</ns5:name>
                    <ns5:value>10/26/2016 06:34 PM</ns5:value>
                </ns5:field>
                <ns5:field>
                    <ns5:name>Duration</ns5:name>
                    <ns5:value>00:57:54</ns5:value>
                </ns5:field>
            </ns5:hit>
        </ns5:hits>
        <ns5:hits selectorValue="797-844-8761">
            <ns5:hit>
                <ns5:field>
                    <ns5:name>Callee #</ns5:name>
                    <ns5:value>667-828-9241</ns5:value>
                </ns5:field>
                <ns5:field>
                    <ns5:name>Caller #</ns5:name>
                    <ns5:value>797-844-8761</ns5:value>
                </ns5:field>
                <ns5:field>
                    <ns5:name>Date/Time</ns5:name>
                    <ns5:value>04/09/2016 10:53 AM</ns5:value>
                </ns5:field>
                <ns5:field>
                    <ns5:name>Duration</ns5:name>
                    <ns5:value>01:07:17</ns5:value>
                </ns5:field>
            </ns5:hit>
        </ns5:hits>
    </ns5:selector>
</ns5:clearTextResponse>
```
