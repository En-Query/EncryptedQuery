# Drop In Encryption 

This release includes an API that allows a developer to support using
alternate homomorphic encryption schemes for PIR processing.  (The API
is currently at an experimental stage and may change in upcoming
releases.)  Support for an alternate encryption scheme is added by
implementing a set of classes that implement the following interfaces:

    PublicKey (java.security.PublicKey)
    PrivateKey (java.security.PrivateKey)
    CipherText (org.enquery.encryptedquery.encryption.CipherText)
    PlainText (org.enquery.encryptedquery.encryption.PlainText)
    ColumnProcessor (org.enquery.encryptedquery.encryption.ColumnProcessor)
    CryptoScheme (org.enquery.encryptedquery.encryption.CryptoScheme)

### PublicKey Class 
The class implementing PublicKey represents an immutable object that
encapsulates cryptographic parameters and public key data that are
needed by the responder to perform public cryptographic operations in
the crypto scheme.  It provides a way for the application to serialize
the data.  (Deserialization is done using a method provided by the
CryptoScheme object.)  The object may optionally provide methods that
return precomputed values for speeding up certain public crypto
operations.  The object must implement the following methods:

    public String getAlgorithm()
    public String getFormat()
    public String getEncoded()

### PrivateKey Class
The class implementing PrivateKey represents an immutable object that
encapsulates the cryptographic parameters and private key data that
are needed by the querier to perform query generation and response
decryption.  It provides a way for the application to serialize the
data.  (Deserialization is done using a method provided by the
CryptoScheme object.)  The object may optionally provide methods that
return precomputed values for speeding up certain private crypto
operations.  The object must implement the following methods:

    public boolean isDestroyed()
    public String getAlgorithm()
    public String getFormat()
    public String getEncoded()

### CipherText Class
The class implementing CipherText represents an immutable object that
encapsulates a ciphertext value in the new crypto scheme for some
parameter set.  It provides a way for the application to serialize the
data.  (Deserialization is done using a method provided by the
CryptoScheme object.)  The object must implement the following
methods:

    public byte[] toBytes()

#### PlainText Class
The class implementing PlainText represents an immutable object that
encapsulates a plaintext value in the crypto scheme for some parameter
set.  It provides a way for the application to serialize this value.
The object must implement the following methods:

    public bytes[] toBytes()

The ColumnProcessor is an object that is used by the responder to
compute individual CipherText values corresponding to a "column" of
plaintext chunks.  The scheme may define multiple ColumnProcessor
classes to implement different algorithms to perform this computation.
(For example, the Paillier crypto scheme defines four different
ColumnProcessor classes: DeRooijColumnProcessor,
DeRooijJNIColumnProcessor, YaoColumnProcessor, and
YaoJNIColumnProcessor.)  A ColumnProcessor object must implement the
following methods:

    void insert(int rowIndex, byte[] input)
    void insert(int rowIndex, byte[] input, int inputOffset, int inputLen)
    CipherText compute()
    void clear()

### CryptoScheme Class
The class implementing CryptoScheme provides all the PIR-specific
crypto operations needed by the EncryptedQuery application.  After
initialization (via the initialize() method), the object also
encapsulates a specific choice of parameters for the crypto scheme.
This class must implement the following methods:

    void initialize(Map<String, String> config)

        This function should be called exactly once after object
    	creation.  The map config is used to specify crypto parameters
    	as well as other runtime arguments, e.g. choice of algorithm
    	and native library paths.

    Iterable<Map.Entry<String, String>> configurationEntries()

        Provides read-only access to the configuration used to
        initialize this CryptoScheme object.

    String name()

        Returns a short name for this CryptoScheme, e.g. "Paillier".

    String description()

        Returns a description of this CryptoScheme

    ColumnProcessor makeColumnProcessor(QueryInfo queryInfo, Map<Integer, CipherText> queryElements)

        This function is called by the responder to return a
        ColumnProcessor object used to compute CipherText values for
        the response.

    KeyPair generateKeyPair()

        Returns a KeyPair object containing a freshly generated pair
        of matching PrivateKey and PublicKey values.  The keys are
        generated according to the parameters and options specified
        during the call to initialize().

    PrivateKey privateKeyFromBytes(byte[] bytes)

        Deserialization function for the PrivateKey object for this
        scheme.

    PublicKey publicKeyFromBytes(byte[] bytes)

        Deserialization function for the PublicKey object for this
        scheme.

    CipherText cipherTextFromBytes(byte[] bytes)

        Deserialization function for the CipherText object for this
        scheme.

    CipherText encryptionOfZero(PublicKey publicKey)

        Returns a CipherText value that is an encryption of the zero
        plaintext value in this scheme.  The encryption does not have
        to be randomized, so this method may return a hard-coded
        value.

    PlainText decrypt(KeyPair keyPair, CipherText cipherText)

        Returns the Plaintext value that results from decrypting the
        given CipherText value using the given keys.

    Stream<PlainText> decrypt(KeyPair keyPair, Stream<CipherText> c)

        Returns a stream of Plaintext values that results from
        decrypting the given stream of CipherText values using the
        given keys.

    Map<Integer, CipherText> generateQueryVector(KeyPair keyPair, QueryInfo queryInfo, Map<Integer, Integer> selectorQueryVecMapping)

        Given a key pair and a mapping from hash values to targeted
        selector number, generates and returns a list of query
        elements that encodes the query in encrypted form.

    CipherText computeCipherAdd(PublicKey publicKey, CipherText left, CipherText right)

        This function performs the homomorphic addition operation on
        two CipherText values and returns the resulting new
        CipherText.

    CipherText computeCipherPlainMultiply(PublicKey publicKey, CipherText left, byte[] right)

        This function performs the homomorphic multiplication on one
        CipherText value and a plaintext data chunk and returns the
        resulting CipherText.

    byte[] plainTextChunk(QueryInfo queryInfo, PlainText plainText, int chunkIndex)

        This function extracts the data chunk from a PlainText object
        at the specified index.
