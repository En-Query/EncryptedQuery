# Encrypted Query Examples

These examples will provide the user with a base upon which to build new queries.

1. The Standalone example will query a Phone Data flat file searching for specific Caller numbers.
2. The Xetra example will use Flink to query trading data searching for records using a specific Mnemonic.
3. The Pcap example has both a standalone data source and a Kafka Streaming source.

## Running the examples
The following commands assume the current directory to be `/opt/encrypted-query/current` in the Responder server:

``` sh 
$ cd /opt/encrypted-query/current
```

Copy over the data schemas and data source files from the `examples` directories.

``` sh
$ tar -xvf  examples/standalone/phone-data.tar.gz -C sampledata
$ tar -xvf  examples/pcap-kafka/pcap-data.tar.gz  -C sampledata
$ cp  examples/responder-files/data-schemas/* inbox/
$ cp  examples/responder-files/data-source-configurations/*  responder/etc/
```

In each example sub-folder there is a README that details the specific example.

