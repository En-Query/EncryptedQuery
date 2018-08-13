Release notes for EncryptedQuery 1.1

1 - This release includes responder support for JDBC data sources.  You can search data returned by a SQL query against a JDBC database.

2 - Decreased the use of BigInteger variables in the responder.  Java is not very memory efficient when it comes to BigInteger variables and therefore when running EncryptedQuery against large datasets it would frequently run out of java heap space.  This latest release signifigantly reduces memory usage in the responder for standalone/streaming/JDBC modes of operation.


Notes on memory usage:
Each field listed in the queryschema.xml file to be returned will require some memory.  So the more fields returned, the more memory used by the application.   Strings are limited in length by the stringbits property.   The default is 128bits or 16 bytes of data for any string field.  This can be set to any value, but this is fixed length so if a return value is not long enough, it will be padded to the stringbits length.

Changing the HashBitSize will change the number of Hash buckets used.   A hash bit size of 12 will allow 4096 unique hashs, 15 will allow 32,768, 18 will allow 262,146.  It is not recommended to use a hash bit size greater than 22.  The higher this setting the less false positives you will receive but it will consume more memory during processing.

Data Partition Size (dps) combines data before encrypting.  This setting is in bits and needs to be in a multiple of 8.   A recommended setting is 16 or 24, but should not exceed 32.

The values that have the largest bearing on memory usage in the responder are:

responder.processing.threads      <-- how many threads are used to process data.>
responder.maxQueueSize            <-- approximate number of records allowed in the queue before loading is paused.   Loading will resume when the queue size returns to 10% of this values.>
responder.computeThreshold        <-- how many records are consumed from the queue by a thread before computing a single value. >

Recommended settings for a small system with 16GB of ram and 4 cores.
responder.processing.threads = 4
responder.maxQueueSize = 100000
responder.computeThreshold = 20000


It is also recommended to use Javas G1 garbage collection when running the responder.
Add this Java option to the responder invocation -XX:+UseG1GC   (Refer to the jdbc example)


