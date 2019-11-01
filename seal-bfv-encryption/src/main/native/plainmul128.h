#ifndef PLAINMUL128
#define PLAINMUL128
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
#include "seal/context.h"

using namespace std;
using namespace seal;

typedef unsigned __int128 ui128;

void PlainMulAccum(const Ciphertext &,const Plaintext &,ui128 *);

void AccumReduce(shared_ptr<const SEALContext::ContextData> &,ui128 *);

ui128 *AccumCreate(shared_ptr<const SEALContext::ContextData> &);

void ToCipher(shared_ptr<SEALContext> &,
	      const parms_id_type &, PublicKey *,const ui128 *,Ciphertext &);

void AccumReset(shared_ptr<const SEALContext::ContextData> &,ui128 *);

#endif
