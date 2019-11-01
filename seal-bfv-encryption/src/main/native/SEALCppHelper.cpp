#include "SEALCppHelper.h"
#include "seal/seal.h"
#define CIPHERBYTES 8192*4*2*8ull

using namespace std;
using namespace seal;

//function that fills a byte array with a given Ciphertext
void ciphertobyte(Ciphertext cipher, uint8_t *data)
{
	uint64_t *cipherdata;
	size_t poly_mod_count=cipher.poly_modulus_degree();
	size_t coeff_mod_count=cipher.coeff_mod_count();
	//for each polynomial in a ciphertext
	for(int i=0;i<2;i++)
	{
		//get the underlying data
		cipherdata=cipher.data(i);
		for(size_t l=0;l<coeff_mod_count;l++,cipherdata+=poly_mod_count)
		{
			//for each coefficient in a polynomial
			for(size_t j=0;j<poly_mod_count;j++)
			{
				//get the coefficient
				uint64_t val=cipherdata[j];
				//for each byte in a coeffient
				for(int k=0;k<8;k++,data++,val>>=8)
				{
					//get the byte and store it in the byte array
					*data=static_cast<uint8_t>(val&0xFF);
				}
			}
		}
	}
}

//function that creates a Ciphertext when given a byte array
// Here, data points to the start of a jbyteArray cipherText.toBytes(),
// which (assuming WLOG that coeff_mod_count = 4) looks like
// c_0 || c_1,
// where the "polynomial" c_i looks like
// (c_i)_0 || (c_i)_1 || ... || (c_i)_{8191}, 
// where the "coefficient" (c_i)_j has CRT representation
// ((c_i)_j)_0 || ((c_i)_j)_1 || ((c_i)_j)_2 || ((c_i)_j)_3,
// where the eight-byte value ((c_i)_j)_k looks like
// (((c_i)_j)_k)_0 || (((c_i)_j)_k)_1 || (((c_i)_j)_k)_2 || (((c_i)_j)_k)_3 || (((c_i)_j)_k)_4 || (((c_i)_j)_k)_5 || (((c_i)_j)_k)_6 || (((c_i)_j)_k)_7,
// where the modulo q_k reduction of the "coefficient" (c_i)_j is
// (((c_i)_j)_k)_7 * (2^8)^7 + (((c_i)_j)_k)_6 * (2^8)^6 + ... + (((c_i)_j)_k)_0 * (2^8)^0.
// In particular, in the byte array cipherText.toBytes(),
// the ciphertext polys are in order (0 followed by 1);
// the coeffs are in order (0 up to 8191);
// the reductions of the coeffs are in order (0 through 3);
// but the 8 bytes in the reductions of the coeffs are given in little endian order
Ciphertext bytetocipher(uint8_t *data,shared_ptr<SEALContext> context,size_t parameternum)
{
	Ciphertext cipher;
	auto context_data=context->context_data();
	for(size_t i=0;i<parameternum;i++)
	{
		context_data=context_data->next_context_data();
	}
	auto parms=context_data->parms();
	cipher.resize(context, parms.parms_id(), 2);
	size_t poly_mod_count=parms.poly_modulus_degree();
	size_t coeff_mod_count=parms.coeff_modulus().size();
	//cout << "coeff_mod_count: " << coeff_mod_count << endl;
	uint64_t *cipherdata;
	//for each polynomial in a ciphertext
	for(int i=0;i<2;i++)
	{
		//get the polynomial
		cipherdata=cipher.data(i);
		// loop over the prime factors of the ciphertext modulus
		for(size_t l=0;l<coeff_mod_count;l++)
		{
			//for each coefficient in a polynomial
			for(size_t j=0;j<poly_mod_count;j++,data+=9,cipherdata++)
			{
				//zero out the coefficient
				*cipherdata=0;
				//go to the last byte
				data+=7;
				//for each byte in a coefficient working backwards
				for(int k=0;k<8;k++,data--)
				{
					//shift the data over
					*cipherdata<<=8;
					//put the byte in the lowest order part of the data
					*cipherdata+=*data;
				}
			}
		}
	}
	cipher.is_ntt_form()=true;
	return cipher;
}

//function that fills the elements of a byte array
//with the data from a given plaintext;
// the poly plain.data() looks like p_0 || ... || p_{8192}, 
// where each coeff p_i is a uint64_t;
// this function returns a java byte array that looks like
// (p_0)' || ... || (p_{8192})',
// where (p_i)' is the eight byte little endian 
// representation of p_i
void plaintobyte(Plaintext plain,uint8_t* data)
{
	uint64_t *plaindata=plain.data();
	size_t size=plain.coeff_count();
	//for each coefficient in a polynomial
	for(size_t i=0;i<size;i++)
	{
		//get the coefficient
		uint64_t val=plaindata[i];
		//for each byte in a coeffient
		for(size_t j=0;j<8;j++,data++,val>>=8)
		{
			//get the low byte and put it in the byte array
			*data=static_cast<uint8_t>(val&0xFF);
		}
	}
}

//function that returns a plaintext with the data from a given byte array
Plaintext bytetoplain(uint8_t* data,int numvals)
{
	Plaintext plain(numvals);
	uint64_t *plaindata=plain.data();
	//for each coeffient in a polynomial
	for(int i=0;i<numvals;i++,plaindata++,data+=9)
	{
		//zero out the coefficient
		*plaindata=0;
		//go to the last byte
		data+=7;
		//for each byte in a coefficient
		for(size_t j=0;j<8;data--,j++)
		{
			//shift the data over 1 byte
			*plaindata<<=8;
			//put the data in the low byte of the coefficient
			*plaindata+=*data;
		}
	}
	return plain;
}

//function that fills a byte array with data from a given pk
void pktobyte(PublicKey pk,uint8_t* data)
{
	ciphertobyte(pk.data(),data);
}

//function that returns a pk using that data from a given byte array
PublicKey bytetopk(uint8_t* data,shared_ptr<SEALContext> context)
{
	PublicKey toreturn;
	//printf("bytetocipher() called by bytetopk\n");
	toreturn.data()=bytetocipher(data,context,0);
	return toreturn;
}

//function that fills a byte array with data from a given sk
void sktobyte(SecretKey sk,uint8_t* data)
{
	plaintobyte(sk.data(),data);
}

//function that returns a sk using data from a given byte array
SecretKey bytetosk(uint8_t* data)
{
	SecretKey toreturn;
	toreturn.data()=bytetoplain(data,32768);
	return toreturn;
}

void rktobyte(RelinKeys rk,uint8_t* data)
{
	auto &rkvec=rk.data()[0];
	for(size_t i=0;i<4;i++,data+=CIPHERBYTES)
	{
		ciphertobyte(rkvec[i],data);	
	}
}

RelinKeys bytetork(uint8_t* data,shared_ptr<SEALContext> context)
{
	vector<vector<Ciphertext>> keys;
	vector<Ciphertext> intermediate;
	Ciphertext toadd;
	for(size_t i=0;i<4;i++,data+=CIPHERBYTES)
	{
		intermediate.push_back(bytetocipher(data,context,0));
	}
	keys.push_back(intermediate);
	RelinKeys toreturn=RelinKeys(keys,60);
	toreturn.parms_id()=context->first_parms_id();
	return toreturn;
}
