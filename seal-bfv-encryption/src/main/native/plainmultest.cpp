#include <iostream>
#include <fstream>
#include <iomanip>
#include <vector>
#include <string>
#include <chrono>
#include <random>
#include <thread>
#include <mutex>
#include <memory>
#include <limits>
#include "seal/seal.h"
#include "plainmul128.h"

using namespace std;
using namespace seal;

typedef chrono::high_resolution_clock Clock;

#define timediff(t1,t2) chrono::duration_cast<chrono::nanoseconds> (t2-t1).count()

#define DIM 8192
#define PLAINMOD 4295049217ull
#define TESTS 1000
#define LOGSEL 13
#define SELECTORS (1ull << LOGSEL)

int main(int argc,char **argv)
{
	uint64_t dim=DIM;
	uint64_t plainmod=PLAINMOD;
	if(argc>1)
		dim=stoi(argv[1]);	
	if(argc>2)
		plainmod=stoi(argv[2]);	
    //Initialization
    EncryptionParameters parms(scheme_type::BFV); 
    parms.set_poly_modulus_degree(dim); 
    parms.set_coeff_modulus(DefaultParams::coeff_modulus_128(dim)); 
    parms.set_plain_modulus(plainmod); 
    auto context = SEALContext::Create(parms,true);
    KeyGenerator keygen(context);
    PublicKey *pkptr;
    auto public_key = keygen.public_key();
    pkptr = &public_key;
    auto secret_key = keygen.secret_key(); 
    RelinKeys relin_keys = keygen.relin_keys(60);
    Decryptor decryptor(context, secret_key); 
    Encryptor encryptor(context, public_key); 
    Evaluator evaluator(context); 
	auto context_data=context->context_data();
	auto parms_id=parms.parms_id();
	srand(time(NULL));
	Plaintext val("10");
	Plaintext plainone("1");
	Plaintext out;
	Ciphertext cipher;
	ui128 *Accum;
	Accum=AccumCreate(context_data);
	encryptor.encrypt(plainone,cipher);
	evaluator.transform_to_ntt_inplace(cipher);
	evaluator.transform_to_ntt_inplace(val,parms_id);
	PlainMulAccum(cipher,val,Accum);
	PlainMulAccum(cipher,val,Accum);
	AccumReduce(context_data,Accum);
	ToCipher(context, parms_id, pkptr, Accum, cipher);
	evaluator.transform_from_ntt_inplace(cipher);
	decryptor.decrypt(cipher,out);
	cout << out.to_string() << endl;
	
	delete[] Accum;
	return 0;
}
