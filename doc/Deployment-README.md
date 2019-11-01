# Encrypted Query Deployment
Encrypted Query is separated into two components: Querier and Responder. The Querier encrypts queries and decrypts results. The Responder executes queries and produces result files.

Encrypted Query has been developed and tested on Centos 7 OS. The application is written in Java and C/C++, and uses Apache Karaf as container.

The Querier and Responder components are designed to run on seperate servers, however, for testing and demonstration purposes
they can be configured to run on the same server.

A set of automated deployment scripts are included in the `deployment` directory.  Out of the box,
these deployment scripts are fully functional for a single-server/embedded-database deployment, most suitable for demo
and testing purposes. For deployment to separate servers, or to use an external MariaDB database server, manual 
configuration (as described later in this document) is needed. 


## Prerequisites
There are potentially three servers involved in building and deploying Encrypted Query:

1. Deployer: Server from which you run the deployment scripts. 
2. Querier: Server where Querier is deployed.
3. Responder: Server where Responder is deployed.

The prerequisites for each server are as follows:

*Deployer:*
	
1. SSH. This server needs password-less SSH access to both Querier and Responder servers.
2. Ruby (version 2.0 or newer)
3. Capistrano
5. wget

*Querier and Responder:*
	
1. Java
2. Maven
3. Git
4. GCC
5. SSH
	  
Please follow the instructions from the following sites to download and istall the prerequisite software:

* Git — https://git-scm.com/
* Maven — https://maven.apache.org/
* GCC — https://developers.redhat.com/blog/2019/03/05/yum-install-gcc-8-clang-6/
* Ruby — https://www.ruby-lang.org/en/
* Java — https://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html
* Capistrano —  https://capistranorb.com/documentation/getting-started/installation/
* wget — https://www.gnu.org/software/wget/
 
## Directories and accounts
By default, the deployment scripts assume that the software is installed in `/opt/encrypted-query` (configurable) 
in both Querier and Responder. Make sure this directory exists in both servers with the appropriate permissions 
for the user account under which Encrypted Query will be running. In the case of local deployment, the current 
user is assumed. For multi-server deployments, a user account needs to be specified.  This user needs to exists
in Querier and Responder servers, although it is not needed in Deployer server. All SSH commands issued during
deployment will use this account.  

## SSH
Deployment scripts are based on Capistrano, which in turn, depends on SSH to copy files and execute remote commands 
from the Deployer server to the Querier and Responder servers. *Important:* SSH access between Querier and
Responder is *not* needed; only Deployer needs to access both Querier and Responder Server over SSH.

For local deployment, the current user account is used, for multi-server account, you need to specify a user account
that must exists in Querier and Responder. 

Github has a very good tutorial on how to setup password-less
access with SSH here https://help.github.com/en/articles/connecting-to-github-with-ssh/
 

## Download
After all prerequisite software has been installed, and password-less SSH access for 
the designated user account (current user in local deployments) is working from 
Deployer to Querier and Responder, you are ready to download (or clone) the sources to the Deployer server.
 
You can download a Zip file directly from https://github.com/En-Query/EncryptedQuery.git or use git command: 

``` sh
$ git clone https://github.com/En-Query/EncryptedQuery.git encrypted-query
```

## Stages/Environments
Deployment scripts are organized by stage (environment) so you can manage each environment
independently. Out of the box, the following stages are available:

1. local — Both Querier and Responder talking to each other in the same local host.
2. local-responder — Only responder in the local host.
3. offline-querier  — Only Querier in offline mode in the local host.

Additional stages can be added, for example for QA, Production, etc. More information 
about that later in this document.

## Deployment commands
Once Encrypted Query sources are downloaded on the Deployer server, you will be issuing various commands 
from the `deployment` directory of Encrypted Query.

``` sh
$ cd encryptedquery/deployment/
```

The deployment scripts come with a ready to use configuration to deploy both Querier and Responder 
to the local host. Notice that you still need
to set password-less SSH access to the local host.

To use this configuration, issue the following command from the `deployment` directory:

``` sh
$ cap local deploy
```

The above command is composed of three parts:

1. `cap`  this is the Capistrano command (all commands will start with this) 
2. `local` this indicates the _stage_ or _environment_ to operate on, in this case the local host. 
3. `deploy` this indicates what to do, in this case full deployment. 

If everything went well, you can visit the Querier Web UI in your browser:

	http://localhost:8182/querier

You may also access each component administrator's console:
For Querier:

``` sh
$ /opt/encrypted-query/current/querier/bin/client
```

For Responder:

``` sh
$ /opt/encrypted-query/current/responder/bin/client
```

As you configure new environments, for example `production`, the command to deploy would be:

``` sh
$ cap production deploy
```

### Tail the logs
You can also tail the Querier and Responder logs simultaneously:

``` sh
$ cap local tail
```

(Substitute `local` with the name of the stage/environment.)

### Show last error
You can also show the last error in Querier and Responder logs:

``` sh
$ cap local last_error
```

(Substitute `local` with the name of the stage/environment.)

### Health Check
To quickly verify that everything is running normal, execute the following command in
the Deployer system terminal:

``` sh
$ cap local health
responder @ localhost -> [OK]
querier @ localhost -> [OK]
```

In case of errors, or not running, the output from this command will be:

``` sh
$ cap local health
responder @ localhost -> [ERROR] ()
querier @ localhost -> [ERROR] ()
```

### Starting and stopping Servers
You can easily start and stop Querier and Responder.

To stop:

``` sh
$ cap local stop
Current status is: Running ...
00:00 stop
      01 /opt/encrypted-query/current/responder/bin/stop
    ✔ 01 localhost 0.855s
Waiting for Karaf to stop on localhost:/opt/encrypted-query/current/responder/bin
Current status is: Running ...
      02 /opt/encrypted-query/current/querier/bin/stop
    ✔ 02 localhost 0.856s
Waiting for Karaf to stop on localhost:/opt/encrypted-query/current/querier/bin
```

To start:

``` sh
$ cap local start
00:00 start
      01 /opt/encrypted-query/current/responder/bin/start
    ✔ 01 localhost 0.033s
Waiting for Karaf to start on localhost:/opt/encrypted-query/current/responder/bin
      02 /opt/encrypted-query/current/querier/bin/start
    ✔ 02 localhost 0.045s
Waiting for Karaf to start on localhost:/opt/encrypted-query/current/querier/bin
```

## Customization
At this point, you should have been able to deploy locally. Next, we will cover how to
customize your deployment by adding new data sources, change the CryptoScheme, use an external
MariaDB database, set up new environments, etc.


## Querier and Responder directory structure
The directory structure created by the deployment scripts in Querier and Responder server is as this:

```
├── current -> /opt/encrypted-query/releases/20191007174508/
├── releases
│   ├── 20191007165449
│   ├── 20191007174508
├── repo
│   └── <git repository>
├── revisions.log
└── shared
    └── <linked_files and linked_dirs>
```

Where:

* `current` is a symlink pointing to the latest release. This symlink is updated at the end of a successful deployment. If the deployment fails in any step the current symlink still points to the old release.
* `releases` holds all deployments in a timestamped folder. These folders are the target of the current symlink. By default, a maximum of 2 releases is kept, as new releases are deployed, the older ones are automatically deleted. 
* `repo` holds a raw git repository (e.g. objects, refs, etc.).
* `revisions.log` is used to log every deploy or rollback. Each entry is timestamped and the executing user is listed. 
* `shared` contains configuration data shared across releases (symlinked into each release directory). 

The _shared_ directory shown above, contains important files and directories that are worth mentioning:

*Querier shared directory:*
	
* `query-key-storage` — holds the encryption keys for the queries 
* `querier-blob-storage` — holds the various blob objects created during normal use such as queries, and response files.
* `derbydb` — Derby database files, if using embedded database engine Derby 
	
*Responder shared directory:*

* `responder-blob-storage` — holds the various blob objects created during normal use such as queries, and response files.
* `results` — holds result files created during query execution
* `sampledata` — holds data files to query against
* `inbox` — directory where to copy data schema files to be imported by Responder
* `jobs` — parent directory holding temporary files during query execution
* `responder-config` — configuration files copied to Karaf's `etc` directory. Replaced with each deployment.  
* `derbydb` — Derby database files, if using embedded database engine Derby 
	
You may want to _symlink_ some of these directories (i.e. `query-key-storage`, `querier-blob-storage`, `sampledata`, `derbydb`, and 
`responder-blob-storage`) to large capacity disks, as they grow over time.

### Scripts directory structure
The `deployment` directory structure (as downloaded from the GIT repository) 
on the Deployer server has a structure like this:

```
├── config
│   ├── inbox
│   ├── sampledata
│   └── stages
│       ├── local
│       │   ├── querier
│       │   └── responder
│       ├── local-responder
│       │   └── responder
│       └── offline-querier
│           └── querier
├── lib
│   └── capistrano
│       └── tasks
└── log
``` 

Normally, you will be making changes to files under the `config` branch, and leave the `lib`
and `log` branches alone. The `lib` directory contains deployment code, and `log` is where 
the deployment log file resides.

The `inbox` directory is used to add data schemas to Responder. You can add/update data schemas 
definitions here that will be imported at runtime by Responder.

The `sampledata` directory contains the sample data file `cdr5.json` included in this release,
you may also add more sample files here to be pushed to remote servers, as all files in this
directory are deployed to Responder.
  
### Global configuration
Directly under the `config` directory, you will find a `deploy.rb` file, this is 
where global configuration of the deployment is held. Configuration specified here 
applies to all deployment stages (environments) but can be overridden by
a stage specific configuration.

Aspects you may want to configure globally in this file are:

#### GIT repository 
You would need to change the GIT repository URL, only if you have a local clone. 
For example, if you are deploying to a closed system 
server that does not have access to the internet.

```
set :repo_url, "https://github.com/En-Query/EncryptedQuery.git"
```

#### Installation directory
This is the directory you want Encrypted Query to be deployed to.  The 
default is:

``` 
set :deploy_to, "/opt/encrypted-query"
```

#### Default environment variables for SSH commands
Since SSH commands run on a non login shell in the remote server, this can be specified
to manipulate the environment the commands run under.  For example, to specify a search path.

```
set :default_env, { path: "/usr/local/bin:$PATH" }
```

#### Number of releases to maintain
As you deploy new releases, how many old releases are kept.
By default we have:

```
set :keep_releases, 2
```

### Per stage (environment) configuration
Each stage (a.k.a. environment) has a configuration
file under `config/stages/` and a directory under `config/deploy/config/` as shown here for the `local` stage:

```
config/
└── stages
    ├── local
    │   ├── querier
    │   └── responder
    └── local.rb        
```

When adding new stages, copy the `deploy/local.rb` file and
the `config/stages/local` directory with the name of the new environment.  For example, this
is how your directory would look if you added a `production` stage/environment.

```
config/
└── stages
    ├── local
    │   ├── querier
    │   └── responder
    ├── production
    │   ├── querier
    │   └── responder
    ├── local.rb
    └── production.rb    
```

Each stage directory contains a `querier` and a `responder` 
sub-directory, this is where configuration files specific to each role 
belong. All files in these directories (excluding any sub-directory)
are copied to its corresponding Karaf's `etc` directory.  For example, files 
in the `querier` directory 
are copied to the Querier server's `/opt/encrypted-query/current/querier/etc`, and
files under the `responder` directory are copied to the 
Responder server's `/opt/encrypted-query/current/responder/etc`.

##### Keeping stages in an external directory
You can keep the Deployer server's `stages` directory outside the deployment directory 
structure by setting the environment variable STAGES_DIR. (This can be convenient to avoid  
cluttering the deployment GIT directory.)

``` sh
$ export STAGES_DIR=~/my-stages
$ cap production deploy
```

Or

``` sh
$ STAGES_DIR=~/my-stages cap production deploy
```
 
 
#### Deploying Data Schemas
Data schemas are deployed to Responder via its `inbox` directory. During normal deployment, files
in the `config/inbox` directory are copied to the Responder server if they are new or modified.
Dot no modify the Data Schema files directly in the Response server, or they will be overwritten 
on the next deployment.

You may also deploy just the `inbox` directory without having to do a *full* deployment:

``` sh
$ cap staging upload_inbox_files
```
 
This allow you to make changes to the Data Schemas (in the Deployer server) and push them
without restarting or re-deploying Responder. 

You can also keep the Deployer server's `inbox` directory outside the deployment directory 
structure by setting the environment variable INBOX_DIRS:

``` sh
$ export INBOX_DIRS=~/my-inbox
$ cap production upload_inbox_files
```

Or

``` sh
$ INBOX_DIRS=~/my-inbox cap production upload_inbox_files
```

Multiple directories can be specified as `inbox` directories:

``` sh
$ INBOX_DIRS=~/inbox1,~/inbox2 cap production upload_inbox_files
```
 
In this case, files from `inbox1` and `inbox2` are pushed to the Responder's `inbox` directory
(if new or modified). If the same file name exists in `inbox1` and `inbox2`, the one from 
`inbox1` is used, and the one from `inbox2` is ignored. 

#### Variable expansion in configuration files
To avoid repeating the deploy directory 
(`/opt/encrypted-query/current` by default)
in configuration files,
the place holder `<%= release_path %>` can be used in its place. 

This is particularly useful if you later decide to 
change the deployment directory (setting `:deploy_to` in the `deploy.rb` file)
as you would not have to replace all configuration files with the new directory.

Other variables can also be added and referenced in configuration files using the placeholder format: `<%= variable %>`

#### Updating configuration
You can update the servers configuration without doing a full deployment. 

``` sh
$ cap local upload_config
```

You should check the health after this.

#### Native libraries
In the stage configuration file (e.g. local.rb), specify if native libraries should be built:

```
set :build_native_libraries, false
```

Set this to _true_, if you want native libraries to be build for this envionment.

#### Database engine
In the stage configuration file (e.g. local.rb), specify the database engine to deploy:

```
set :database_engine, "derbydb"
```

Possible values are: `mariadb` and `derbydb`.  Note that this setting is not sufficient
to change the database engine, you will also need to update the `org.ops4j.datasource-querier.cfg`
or `encrypted.query.responder.data.cfg` files. More on that later.

#### Server information
In order for the deployment scripts to push files and execute commands
in the Querier and Responder servers, it needs to know some information about them.
You can specify the servers belonging to each stage (environment) along with their role
and properties in the stage configuration file (e.g. local.rb).

Server definition has the form:

server <host>, [user: "<user>",] roles: %w{<roles>}, GPU_support: <gpu>

Where:

* host — Host name or (local) IP address of the server.
* user: — User account to use to connect to this server. Optional, defaults to current user.
* roles: — Roles this server plays. Possible values are _querier_ and _responder_. Multiple roles can be specified separated by space.
* gpu: — Whether this server supports GPU. (CUDA library required)

For example:

```
server "responder.local", user: "radmin", roles: %w{responder}, GPU_support: true
server "192.168.12.15", user: "qadmin", roles: %w{querier}
```

Defines two servers (responder.local with `responder` role, and 192.168.12.15 with `querier` role), 
the user name to log in over SSH (radmin and qadmin), and indicates that the responder.local server 
has support for GPU (i.e., GPU libraries will be built in this server). 

or

```
server "localhost", roles: %w{responder querier}, GPU_support: false
```

Defines a single server (localhost), with both roles (responder and querier), and no GPU support.

In addition to provide information for SSH connectivity, capabilities, and roles, you will also need to   
specify HTTP connectivity details by role:

```
set :api_endpoints, {
  "responder" => {
    "scheme" => "http",
    "host" => "responder.pir.com", 
    "port" => "8181", 
    "path" => "/responder"
  },
  "querier" => {
      "scheme" => "http",
      "host" => "querier.pir.com", 
      "port" => "8182", 
      "path" => "/querier"
  }
}
```

These properties are used to connect to the health status URL, as well as to configure the Integration component. The `host` entries should be the externally accessible
host name (may be different from the IP used for SSH), as opposed to local IP addresses.
 
See sample in `local` environment configuration files.

#### Hash bit size
The desired hash bit size for queries can be configured by setting the 
`:hashBitSize` variable in the stage configuration file (e.g. local.rb).  For example:

```
set :hashBitSize, 15
```

#### Crypto scheme
The desired Crypto Scheme to use for queries can be configured by setting the 
`:cryptoScheme` variable in the stage configuration file (e.g. local.rb).  For example:
 
```
set :cryptoScheme, "Paillier"
```

Possible values are:

1. "Seal-BFV" — For Seal BFV Crypto Scheme
2. "Null" — For Null Crypto Scheme (clear text)
3. "Paillier" — For Paillier Crypto Scheme (plain text)

#### Database connectivity
Sample configuration files are provided in the local environment 
directories:

* org.ops4j.datasource-responder.cfg  —  Responder Database connectivity
* org.ops4j.datasource-querier.cfg  —  Querier Database connectivity

The local environment is configured to use the embedded DerbyDB database, 
therefore you would not need to make any changes for the local environment. 
Derby is configured to persists its data in the `/opt/encrypted-query/shared/derby`
directory. This allows the Derby database to be preserved across releases.

To clear the Derby database and start fresh, you can (while Karaf is stopped), 
delete the `querier` and/or `responder`
directories under `/opt/encrypted-query/shared/derby`. 
The database will be automatically recreated when Karaf is started and the directory
does not exists.

If you are adding other environments, you would need to copy the appropriate 
`org.ops4j.datasource-*.cfg` file to the `querier` or `responder` directory 
and modify them to suit your needs.

Normally, this would be needed only for MariaDB settings:

* url — JDBC URL to the database.
* user — User name to connect to database
* password — Password to connecto the database

Please refer to MariaDB documentation on the URL format for connecting to a MariaDB 
database:  https://mariadb.com/kb/en/library/about-mariadb-connector-j/#connection-strings


#### Blob storage configuration
Large data objects, like queries, response files, etc. are stored in disk. As configured
for the local environment, the deployment scripts have dedicated directories under shared 
for these.  That is:

* /opt/encrypted-query/shared/querier-blob-storage 
* /opt/encrypted-query/shared/responder-blob-storage
* /opt/encrypted-query/shared/query-key-storage 
 
(The shared directory is preserved when a new release is deployed.)

You may want to *symlink* these directories to a high capacity disk.

#### Adding data sources
To add a new data source, copy the data source configuration file to the `responder` directory of the stage:

``` sh
$ cp mydatasource.cfg  config/deploy/config/<stage>/responder
```
 
 Where:
 
 * mysdatasouce.cfg — Data Source configuration file.
 * stage — The stage (e.g. local, production, etc.)
  
The next time you deploy to this stage, the new data source file will be pushed to the Responder server.
