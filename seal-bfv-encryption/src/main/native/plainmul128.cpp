#include "plainmul128.h"
using namespace std;
using namespace seal;

// here Plain is regarded as being in ntt domain
void PlainMulAccum(const Ciphertext &Cipher, const Plaintext &Plain, ui128 *Accum)
{
	//we only want degree 2 polynomials
	size_t polysize=2; //Cipher.size();
	size_t datasize=Plain.coeff_count();
	const uint64_t *plaindata=Plain.data();
	for(size_t i=0; i<polysize; Accum+=datasize, i++)
	{
		const uint64_t *cipherdata=Cipher.data(i);
		for(size_t j=0;j<datasize; j++)
		{
			Accum[j]+=(ui128)plaindata[j]*(ui128)cipherdata[j];
		}
	}
}

ui128 *AccumCreate(shared_ptr<const SEALContext::ContextData> &context_data)
{
	auto &parms=context_data->parms();
	size_t poly_degree=parms.poly_modulus_degree();
	size_t coeff_mod_num=parms.coeff_modulus().size();
	size_t elems=poly_degree*coeff_mod_num*2;
	ui128 *Accum=new ui128[elems];
	memset(Accum,0,elems*sizeof(*Accum));
	return Accum;
}

void ToCipher(shared_ptr<SEALContext> &context,
	      const parms_id_type &parms_id, PublicKey *pkptr, const ui128 *Accum,Ciphertext &Cipher)
{
	auto context_data=context->context_data(parms_id);
	auto &parms=context_data->parms();
	size_t poly_degree=parms.poly_modulus_degree();
	size_t coeff_mod_num=parms.coeff_modulus().size();
	size_t elems=poly_degree*coeff_mod_num;

        Encryptor encryptor(context, *pkptr);
        Plaintext plain0("0");
        Ciphertext encryption_of_zero;
        encryptor.encrypt(plain0, encryption_of_zero);
        Evaluator evaluator(context);
	evaluator.mod_switch_to_inplace(encryption_of_zero, parms_id);
	evaluator.transform_to_ntt_inplace(encryption_of_zero);
	
	const ui128 *accumptr=Accum;
	
	size_t accumulator_is_transparent = 1;
        for (size_t k=0; k<2*elems; k++){
          if (accumptr[k] != 0){
            accumulator_is_transparent = 0;
	    break;
	  }
	}

	
	Cipher = Ciphertext(context, parms_id, 2, MemoryManager::GetPool());
	Cipher.resize(2);
	Cipher.is_ntt_form() = true;
	
	for(size_t i=0; i<2; accumptr+=elems, i++){
	  uint64_t *cipherdata=Cipher.data(i);
	  for (size_t j=0; j<elems; j++){
	    if (accumulator_is_transparent == 1){
	      cipherdata[j] = *(encryption_of_zero.data(i) + j);
	    } else {
              cipherdata[j] = accumptr[j];  // i.e., cipherdata[j] = *(accumptr + j)
	    }
	  }
	}
}

void AccumReduce(shared_ptr<const SEALContext::ContextData> &context_data,ui128 *Accum)
{
	auto &parms=context_data->parms();
	size_t poly_degree=parms.poly_modulus_degree();
	auto coeff_mod=parms.coeff_modulus();
	size_t coeff_mod_num=coeff_mod.size();
	for(size_t i=0;i<2;i++)
	{
		for(size_t j=0;j<coeff_mod_num;j++)
		{
			auto modulus=coeff_mod[j];
			uint64_t barrettvals[2];
			for(size_t k=0;k<poly_degree;Accum++,k++)
			{
				barrettvals[0]=(*Accum) ;
				barrettvals[1]=(*Accum)>>64;
				*(Accum)=util::barrett_reduce_128(
					barrettvals,modulus);
			}
		}
	}
}

void AccumReset(shared_ptr<const SEALContext::ContextData> &context_data
	,ui128 *Accum)
{
	auto &parms=context_data->parms();
	size_t poly_degree=parms.poly_modulus_degree();
	size_t coeff_mod_num=parms.coeff_modulus().size();
	size_t elems=poly_degree*coeff_mod_num*2;
	memset(Accum,0,elems*sizeof(*Accum));
}

/*
inline uint64_t barrett_reduce128(ui128 &val, const SmallModulus &m)
{
	const uint64_t Q=m.value();
	auto M=m.const_ration();
	uint64_t M1=M[0];
	uint64_t M2=M[1];
	uint64_t lower=val&mask;
	uint64_t upper=val>>64;
	uint64_t q=M1*upper+M2*lower;
	uint64_t res=lower-Q*q;
	return res;
}*/
