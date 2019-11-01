# How to switch CryptoScheme
EcryptedQuery ships with 3 Crypto Schemes out of the box; however, only one is active/selected in Querier at any given time. In the case of Responder, there is no need to select a preferred Crypto Scheme, since this is only relevant for Query Encryption, which is done by the Querier. Once the Query is encrypted, the name of the selected Crypto Scheme is included in the Query. During query execution, Responder will read this name and use the appropriate Crypto Scheme to process the query.

By default, `Paillier` Crypto Scheme is active in Querier, but you may change it from the adminstratitive console:

	<QUERIER_INSTALLATION_PATH>/bin/client

First, we can inspect the currently deployed Crypto Schemes:

```
service:list CryptoScheme
[org.enquery.encryptedquery.encryption.CryptoScheme]
----------------------------------------------------
 component.id = 9
 component.name = org.enquery.encryptedquery.encryption.nullcipher.NullCipherCryptoScheme
 felix.fileinstall.filename = file:/opt/enquery/encryptedquery-responder-dist-2.2.3-SNAPSHOT/etc/org.enquery.encryptedquery.encryption.nullcipher.NullCipherCryptoScheme.cfg
 name = Null
 nullcipher.padding.byte.size = 385
 nullcipher.plaintext.byte.size = 383
 service.bundleid = 31
 service.id = 261
 service.pid = org.enquery.encryptedquery.encryption.nullcipher.NullCipherCryptoScheme
 service.scope = bundle
Provided by :
 EncryptedQuery :: Null Encryption (31)
Used by:
 EncryptedQuery :: Core (23)

[org.enquery.encryptedquery.encryption.CryptoScheme, org.enquery.encryptedquery.encryption.CryptoSchemeSpi]
-----------------------------------------------------------------------------------------------------------
 component.id = 8
 component.name = org.enquery.encryptedquery.encryption.paillier.PaillierCryptoScheme
 felix.fileinstall.filename = file:/opt/enquery/encryptedquery-responder-dist-2.2.3-SNAPSHOT/etc/org.enquery.encryptedquery.encryption.paillier.PaillierCryptoScheme.cfg
 name = Paillier
 paillier.column.processor = GPU
 paillier.encrypt.query.method = FastWithJNI
 paillier.encrypt.query.task.count = 2
 paillier.gpu.libresponder.busy.policy = GPUNow
 paillier.mod.pow.class.name = org.enquery.encryptedquery.encryption.impl.ModPowAbstractionGMPImpl
 paillier.modulus.bit.size = 3072
 paillier.prime.certainty = 128
 service.bundleid = 32
 service.id = 265
 service.pid = org.enquery.encryptedquery.encryption.paillier.PaillierCryptoScheme
 service.scope = bundle
Provided by :
 EncryptedQuery :: Paillier Encryption (32)
Used by:
 EncryptedQuery :: Core (23)

[org.enquery.encryptedquery.encryption.CryptoScheme]
----------------------------------------------------
 component.id = 10
 component.name = org.enquery.encryptedquery.encryption.seal.SEALCryptoScheme
 name = Seal-BFV
 service.bundleid = 38
 service.id = 262
 service.scope = bundle
Provided by :
 EncryptedQuery :: Seal Brakersy-Fan-Vercauteran Encryption (38)
Used by:
 EncryptedQuery :: Core (23)
```

 As you can see, we have 3 deployed CryptoSchemes with the names:

 1. Null
 2. Paillier
 3. Seal-BFV

 ** Note - Seal-BFV is provided for research purposes only. 


To view which one is currently active:

```
karaf@root()> config:list "(service.pid=org.enquery.encryptedquery.querier.encrypt.EncryptQuery)"
----------------------------------------------------------------
Pid:            org.enquery.encryptedquery.querier.encrypt.EncryptQuery
BundleLocation: ?
Properties:
   crypto.target = Paillier
   felix.fileinstall.filename = file:/opt/enquery/encryptedquery-querier-dist-2.2.3-SNAPSHOT/etc/org.enquery.encryptedquery.querier.encrypt.EncryptQuery.cfg
   service.pid = org.enquery.encryptedquery.querier.encrypt.EncryptQuery
```
You can see the name of the selected Crytpo Schme in under the Properties section of the above output, the property `crypto.target` holds the name of the selected Cryto Scheme.

To change it:

```
karaf@root()> config:property-set -p org.enquery.encryptedquery.querier.encrypt.EncryptQuery  crypto.target "(name=Seal-BFV)"
```

# How to enable SSL for Rest and Web interfaces

In the `etc` directory, locate file `org.ops4j.pax.web.cfg`. By default the HTTP service listens on port 8181 you can change the port by updating this file with the following:

	org.osgi.service.http.port=8181

Or from the terminal console:

	root@karaf> config:property-set -p org.ops4j.pax.web org.osgi.service.http.port 8181

Note: if you want to use port numbers < 1024, remember you have to run with root privileges.

The first step is to create a keystore containing a server certificate. For instance the following command creates a keystore with a self-signed certificate:

	keytool -genkey -keyalg RSA -alias selfsigned -keystore keystore -storepass karaf1234 -validity 360 -keysize 2048

Now, we can enable and configure the HTTPs connector with this keystore in `etc/org.ops4j.pax.web.cfg`:

```
	org.osgi.service.http.port.secure=8443
	org.osgi.service.http.secure.enabled=true
	org.ops4j.pax.web.ssl.keystore=/path/to/keystore
	org.ops4j.pax.web.ssl.password=foo
	org.ops4j.pax.web.ssl.keypassword=karaf1234
```

Property `org.ops4j.pax.web.ssl.keystore` is set to the path to the keystore to be used. If not set the default path `${user.home}/.keystore` is used.

Property `org.ops4j.pax.web.ssl.password` is set to the password used for keystore integrity check. The value can be in plain text or obfuscated (starting with `OBF:`) as described in jetty docummentation (see https://wiki.eclipse.org/Jetty/Howto/Configure_SSL  and  https://wiki.eclipse.org/Jetty/Howto/Secure_Passwords).


Property `org.ops4j.pax.web.ssl.keypassword` is set to the password used for accessing the key from the keystore. The value can be in plain text or obfuscated (starting with `OBF:`).

It’s possible to use only HTTPs and to disable the HTTP using:

	org.osgi.service.http.enabled=false
	org.osgi.service.https.enabled=true


The property `org.ops4j.pax.web.ssl.keystore.type` specifies the keystore type. Defaults to JKS.


The property `org.ops4j.pax.web.ssl.clientauthwanted` specifies if certificate-based client authentication at the server is “wanted”.

The property `org.ops4j.pax.web.ssl.clientauthneeded` specifies if certificate-based client authentication at the server is “required”.

The property `org.ops4j.pax.web.listening.addresses` specifies the comma separated list of addresses to bind the HTTP service to. (e.g. `localhost` or `localhost,10.0.0.1`). Host names or IP addresses can be used. Default value is `0.0.0.0`.


For more information about how to create a server certificate, visit: https://wiki.eclipse.org/Jetty/Howto/Configure_SSL#Generating_Key_Pairs_and_Certificates
