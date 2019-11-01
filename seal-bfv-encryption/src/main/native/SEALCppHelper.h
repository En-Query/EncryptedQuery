#ifndef SEALCPPHELPER_H
#define SEALCPPHELPER_H

#include "seal/seal.h"
using namespace seal;
using namespace std;

//function that creates a Ciphertext when given a byte array
Ciphertext bytetocipher(uint8_t* data,shared_ptr<SEALContext> context,size_t parameternum);

//function that returns a plaintext with the data from a given byte array
Plaintext bytetoplain(uint8_t* data,int numvals);

//function that returns a pk using that data from a given byte array
PublicKey bytetopk(uint8_t* data,shared_ptr<SEALContext> context);

//function that returns a sk using data from a given byte array
SecretKey bytetosk(uint8_t* data);

//function that returns a rk using data from a given byte array
RelinKeys bytetork(uint8_t* data,shared_ptr<SEALContext> context);

//function that fills a byte array with a given Ciphertext
void ciphertobyte(Ciphertext cipher,uint8_t* data);

//function that fills the elements of a byte array
//with the data from a given plaintext
void plaintobyte(Plaintext plain,uint8_t* data);

//function that fills a byte array with data from a given pk
void pktobyte(PublicKey pk,uint8_t* data);

//function that fills a byte array with data from a given sk
void sktobyte(SecretKey sk,uint8_t* data);

//function that fills a byte array with data from a given rk
void rktobyte(RelinKeys rk,uint8_t*data);

#endif
