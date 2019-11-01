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
#include "smallntt.h"

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
    auto public_key = keygen.public_key();
    auto secret_key = keygen.secret_key(); 
    RelinKeys relin_keys = keygen.relin_keys(60);
    Decryptor decryptor(context, secret_key); 
    Encryptor encryptor(context, public_key); 
    Evaluator evaluator(context); 
	uint64_t logn=util::get_power_of_two(dim);
	uint64_t sellen=dim/SELECTORS;
	auto context_data=context->context_data();
	context_data=context_data->next_context_data();
	uint64_t coeff_count=parms.coeff_modulus().size();
	util::SmallNTTTables table(logn,plainmod);
	util::SmallNTTTables fourtable;
	uint64_t _root=table.get_from_root_powers(util::reverse_bits(SELECTORS,logn));
	fourtable.generate(logn-LOGSEL,plainmod,_root);
	util::SmallNTTTables* ntttables= new util::SmallNTTTables[coeff_count];
	tablegen(ntttables,context_data,logn-LOGSEL);
	srand(time(NULL));
	Plaintext val(dim);
	auto t1=Clock::now();
	auto t2=Clock::now();
	uint64_t genduration=0;
	uint64_t nttduration=0;
	for(size_t i=0;i<TESTS;i++)
	{
		t1=Clock::now();
		for(size_t j=0;j<sellen;j++)
		{
			val[j]=rand()%plainmod;
		}
		util::inverse_ntt_negacyclic_harvey(val.data(),fourtable);
		t2=Clock::now();
		genduration+=timediff(t1,t2);
		val.parms_id()=parms_id_zero;
		t1=Clock::now();
		smallntt(val,ntttables,sellen,context_data);
		t2=Clock::now();
		nttduration+=timediff(t1,t2);
	}
	cout << "Selectors:\t" << SELECTORS << endl;
	cout << "Generation:\t" << 1e6*TESTS/genduration << " khz" << endl;
	cout << "NTT:\t\t" << 1e9*TESTS/nttduration << " Hz" << endl;
	
	delete[] ntttables;
	return 0;
}
