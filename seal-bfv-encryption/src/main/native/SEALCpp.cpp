/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
#include "org_enquery_encryptedquery_encryption_seal_SEALCpp.h"
#include <jni.h>
#include "seal/seal.h"
#include "SEALCppHelper.h"
#include "plainmul128.h"
#include "smallntt.h"
using namespace std;
using namespace seal;
#define FINALBYTES 8192*2*8ull
#define CIPHERBYTES 8192*2*4*8ull
#define PLAINBYTES 8192*8
//#define TESTING
#ifdef TESTING
static void* testcontext;
static void* testdecryptptr;
#endif

JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_initkeygen(JNIEnv *env, jclass _class,jlong contextptr)
{
	try {
		(void)_class;
		(void)env;
		shared_ptr<SEALContext> context=
			*(shared_ptr<SEALContext> *)contextptr;
		KeyGenerator *keygen=new KeyGenerator(context);
		return reinterpret_cast<jlong>(keygen);
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}

	return 0;
}

JNIEXPORT jbyteArray JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_getpk(JNIEnv *env, jclass _class,jlong keyptr)
{
	try {
		(void)_class;
		(void)env;
		KeyGenerator &keygen=*reinterpret_cast<KeyGenerator *>(keyptr);
		PublicKey public_key=keygen.public_key();
		jbyteArray toreturn = env->NewByteArray(CIPHERBYTES);
		jbyte * accessor = env->GetByteArrayElements(toreturn,NULL);
		pktobyte(public_key,reinterpret_cast<uint8_t *>(accessor));
		env->ReleaseByteArrayElements(toreturn,accessor,0);
		return toreturn;
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
	return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_getsk(JNIEnv *env, jclass _class,jlong keyptr)
{
	try {
		(void)_class;
		(void)env;
		KeyGenerator &keygen=*reinterpret_cast<KeyGenerator *>(keyptr);
		SecretKey secret_key=keygen.secret_key();
		jbyteArray toreturn = env->NewByteArray(4*PLAINBYTES);
		jbyte * accessor = env->GetByteArrayElements(toreturn,NULL);
		sktobyte(secret_key,reinterpret_cast<uint8_t *>(accessor));
		env->ReleaseByteArrayElements(toreturn,accessor,0);
		#ifdef TESTING
		shared_ptr<SEALContext> context=
			*reinterpret_cast<shared_ptr<SEALContext> *>(testcontext);
		testdecryptptr=new Decryptor(context,secret_key);
		#endif
		return toreturn;
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
	return NULL;
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_destroykeygen(JNIEnv *env, jclass _class, jlong keyptr)
{
	try {
		(void)_class;
		(void)env;
		if (reinterpret_cast<KeyGenerator *>(keyptr) != NULL){
		    delete reinterpret_cast<KeyGenerator *>(keyptr);
		}
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
}

JNIEXPORT jbyteArray JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_cipheradd(JNIEnv *env, jclass _class,jlong conptr,jbyteArray lhs,jbyteArray rhs)
{
	try {
		(void)_class;
		shared_ptr<SEALContext> context=
			*reinterpret_cast<shared_ptr<SEALContext> *>(conptr);
		jbyte *l=env->GetByteArrayElements(lhs,NULL);
		jbyte *r=env->GetByteArrayElements(rhs,NULL);
		Ciphertext lcipher=bytetocipher(reinterpret_cast<uint8_t*>(l),context,0);
		Ciphertext rcipher=bytetocipher(reinterpret_cast<uint8_t*>(r),context,0);
		Evaluator eval(context);
		Ciphertext res;
		eval.add(lcipher,rcipher,res);
		jbyteArray toreturn = env->NewByteArray(CIPHERBYTES);
		jbyte * accessor = env->GetByteArrayElements(toreturn,NULL);
		ciphertobyte(res,reinterpret_cast<uint8_t *>(accessor));
		env->ReleaseByteArrayElements(lhs,l,0);
		env->ReleaseByteArrayElements(rhs,r,0);
		env->ReleaseByteArrayElements(toreturn,accessor,0);
		return toreturn;
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
	return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_plainmul(JNIEnv *env, jclass _class,jlong conptr,jbyteArray cipher,jbyteArray plain)
{
	try {
		(void)_class;
		shared_ptr<SEALContext> context=
			*reinterpret_cast<shared_ptr<SEALContext> *>(conptr);
		jbyte *c=env->GetByteArrayElements(cipher,NULL);
		jbyte *p=env->GetByteArrayElements(plain,NULL);
		Ciphertext ciph=bytetocipher(reinterpret_cast<uint8_t *>(c),context,0);
		Plaintext pl=bytetoplain(reinterpret_cast<uint8_t *>(p),8192);
		Ciphertext res;
		Evaluator eval(context);
		eval.multiply_plain(ciph,pl,res);
		jbyteArray toreturn = env->NewByteArray(CIPHERBYTES);
		jbyte * accessor = env->GetByteArrayElements(toreturn,NULL);
		ciphertobyte(res,reinterpret_cast<uint8_t *>(accessor));
		env->ReleaseByteArrayElements(cipher,c,0);
		env->ReleaseByteArrayElements(plain,p,0);
		env->ReleaseByteArrayElements(toreturn,accessor,0);
		return toreturn;
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
	return NULL;
}

JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_createencryptor(JNIEnv *env, jclass _class,jlong contextptr,jbyteArray pk)
{
	try {
		(void)_class;
		shared_ptr<SEALContext> context=
			*reinterpret_cast<shared_ptr<SEALContext> *>(contextptr);
		jbyte *p = env->GetByteArrayElements(pk, NULL);
		PublicKey pub = bytetopk(reinterpret_cast<uint8_t *>(p), context);
		Encryptor *encrypt=new Encryptor(context, pub);
		env->ReleaseByteArrayElements(pk, p, 0);
		return reinterpret_cast<jlong>(encrypt);
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
	return 0;
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_destroyencryptor(JNIEnv *env, jclass _class,jlong encryptorptr)
{
	try {
		(void)_class;
		(void)env;
		if (reinterpret_cast<Encryptor *>(encryptorptr) != NULL){
		    delete reinterpret_cast<Encryptor *>(encryptorptr);
		}
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
}

JNIEXPORT jbyteArray JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_encryptzero(JNIEnv *env, jclass _class, jlong contextptr, jlong encryptorptr, jint parameternum)
{
	try {
		(void)_class;

		Encryptor &encryptor=*reinterpret_cast<Encryptor *>(encryptorptr);
		Plaintext plain0("0");
		Ciphertext res;
		encryptor.encrypt(plain0,res);

		shared_ptr<SEALContext> context=
			*reinterpret_cast<shared_ptr<SEALContext> *>(contextptr);
		auto context_data=context->context_data();
		for(size_t i=0; i < (size_t)parameternum; i++){
		  context_data=context_data->next_context_data();
		}
		auto parms=context_data->parms();
		Evaluator evaluator(context);
		evaluator.mod_switch_to_inplace(res, parms.parms_id());

		jbyteArray toreturn = env->NewByteArray(((int)(CIPHERBYTES / 4)) * (4 - parameternum));
		jbyte *accessor = env->GetByteArrayElements(toreturn,NULL);
		ciphertobyte(res,reinterpret_cast<uint8_t *>(accessor));
		env->ReleaseByteArrayElements(toreturn,accessor,0);
		return toreturn;
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
	return NULL;
}

// Note: The array indices represents an ordered list of the targeted selectors T
// such that the base-sublen development of Hash(T) as a certain digit a in a certain 
// place p; the values a and p are unimportant here; it matters only that they are 
// fixed prior to the calling of this method  
JNIEXPORT jbyteArray JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_encryptsel(JNIEnv *env, jclass _class,jlong encryptorptr,jlong contextptr,jintArray indices, jint numsel)
{
	try {
		//this function creates an encryption of the query term chosen;
		// this encryption generally corresponds to multiple selectors

		(void)_class;
		Encryptor &encryptor=*reinterpret_cast<Encryptor *>(encryptorptr);
		shared_ptr<const SEALContext> context=
			*reinterpret_cast<shared_ptr<SEALContext> *>(contextptr);
		auto context_data=context->context_data();

		auto &table = context_data->plain_ntt_tables();
		//util::SmallNTTTables table(13,context_data->parms().plain_modulus().value());

		// create a plaintext element, regarded as being in the NTT domain
		Plaintext plain(8192);

		// compute the number of NTT slots allotted per targeted selector
		uint64_t sellen = 8192 / static_cast<uint64_t>(numsel);

		// provide native access to the array of selector indices
		jint *indaccess=env->GetIntArrayElements(indices,NULL);

		// compute the total number of targeted selectors sharing the 
		// given (but here unknown) digit in the specified (but here unknown) 
		// place in the base-sublen developments of their hashes
		jint inlen = env->GetArrayLength(indices);

		// for each targeted selector under consideration here
		for (int j=0; j < inlen; j++){
		  // go to the start of its band of allotted NTT slots
		  uint64_t startpoint = sellen * (static_cast<uint64_t>(indaccess[j]));
		  // set the value of each NTT coefficient within this band to unity
		  for (size_t i=0; i < sellen; i++){
		    plain[util::reverse_bits(startpoint + i, 13)] = 1;
		  }
		}

		// transform the plaintext plain from the NTT domain to the poly domain
		util::inverse_ntt_negacyclic_harvey(plain.data(),*table.get());

		// create a ciphertext
		Ciphertext res;

		// store an encryption of plain in res
		encryptor.encrypt(plain,res);

		// create a serialization of res for use within java
		jbyteArray toreturn = env->NewByteArray(CIPHERBYTES);

		// provide access to res from toreturn 
		jbyte *accessor = env->GetByteArrayElements(toreturn,NULL);
		ciphertobyte(res,reinterpret_cast<uint8_t*>(accessor));

		// don't be greedy, set the bytes free
		env->ReleaseByteArrayElements(toreturn,accessor,0);
		env->ReleaseIntArrayElements(indices,indaccess,0);

		// send back to java a serialization of an encryption 
		// of the above plaintext
		return toreturn;
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
	return NULL;
}

JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_createdecryptor(JNIEnv *env, jclass _class,jlong contextptr,jbyteArray sk)
{
	try {
		(void)_class;
		jbyte * s = env->GetByteArrayElements(sk,NULL);
		SecretKey sec=bytetosk(reinterpret_cast<uint8_t *>(s));
		shared_ptr<SEALContext> context=
			*reinterpret_cast<shared_ptr<SEALContext> *>(contextptr);
		sec.parms_id()=context->first_parms_id();
		Decryptor *decrypt=new Decryptor(context,sec);
		env->ReleaseByteArrayElements(sk,s,0);
		return reinterpret_cast<jlong>(decrypt);
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}

	return 0;
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_destroydecryptor(JNIEnv *env, jclass _class,jlong decryptorptr)
{
	try {
		(void)_class;
		(void)env;
		if (reinterpret_cast<Decryptor *>(decryptorptr) != NULL){
		    delete reinterpret_cast<Decryptor *>(decryptorptr);
		}
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
}

JNIEXPORT jbyteArray JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_decrypt(JNIEnv *env, jclass _class,jlong conptr,jlong decryptorptr,jbyteArray cipher)
{
	try {
		(void)_class;
		shared_ptr<SEALContext> context=
			*reinterpret_cast<shared_ptr<SEALContext> *>(conptr);
		Decryptor &decryptor=*reinterpret_cast<Decryptor *>(decryptorptr);
		jbyte *c=env->GetByteArrayElements(cipher,NULL);
		// recall: cipher = cipherText.toBytes()
		// assuming WLOG that coeff_mod_count = 4,
		// the jbyteArrary cipher looks like
		// c_0 || c_1,
		// where the polynomial c_i looks like
		// (c_i)_0 || (c_i)_1 || ... || (c_i)_{8191}, 
		// where the coefficient (c_i)_j has CRT representation
		// ((c_i)_j)_0 || ((c_i)_j)_1 || ((c_i)_j)_2 || ((c_i)_j)_3,
		// where the eight-byte value ((c_i)_j)_k looks like
		// (((c_i)_j)_k)_0 || (((c_i)_j)_k)_1 || (((c_i)_j)_k)_2 (((c_i)_j)_k)_3 || (((c_i)_j)_k)_4 || (((c_i)_j)_k)_5 || (((c_i)_j)_k)_6 || (((c_i)_j)_k)_7,
		// where the modulo q_k reduction of the "coefficient" (c_i)_j is
		// (((c_i)_j)_k)_7 * (2^8)^7 + (((c_i)_j)_k)_6 * (2^8)^6 + ... + (((c_i)_j)_k)_0 * (2^8)^0.
		// In particular, in the byte array cipherText.toBytes(),
		// the ciphertext polys are in order (0 followed by 1);
		// the coeffs are in order (0 up to 8191);
		// the reductions of the coeffs are in order (0 through 3);
		// but the 8 bytes in the reductions of the coeffs are given in little endian order
		//printf("bytetocipher() called by decrypt\n");
		Ciphertext ciph = bytetocipher(reinterpret_cast<uint8_t *>(c),context,3);
		Plaintext res;
		ciph.is_ntt_form()=false;
		//cout << decryptor.invariant_noise_budget(ciph) <<endl;
		//#ifdef TESTING
		//cout << reinterpret_cast<Decryptor *>(testdecryptptr)->invariant_noise_budget(ciph) << endl;
		//#endif
		decryptor.decrypt(ciph,res);
		res.resize(8192);
		jbyteArray toreturn = env->NewByteArray(PLAINBYTES); // 8*8192 bytes
		jbyte *accessor = env->GetByteArrayElements(toreturn,NULL);
		plaintobyte(res,reinterpret_cast<uint8_t *>(accessor));
		env->ReleaseByteArrayElements(cipher,c,0);
		env->ReleaseByteArrayElements(toreturn,accessor,0);
		return toreturn;
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
	return NULL;
}

/****************************************************************************************************************/

// index1 labels an "ntt-cached" subquery element, whereas
// index2 labels a "computed-on-the-fly" subquery element;
// there is one accumulator per each of the latter;
// this method constructs a plaintext pl by periodically 
// extending the bytes in bytes[], which constitute a data chunk 
// corresponding to the (index1 + sublen * index2)-th database 
// entry, across the entire NTT domain, 
// and then plaintext-ciphertext multiplying this plaintext 
// with the index1-th "ntt-cached" subquery element,
// and then adding the result to the accumulator corresponding 
// to the index2-nd "computed-on-the-fly" subquery element 
JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_insert(
	JNIEnv *env, 
	jclass _class,
	jint index1,
	jint index2,
	jbyteArray bytes,
	jlong contextptr,
	jlong ciphrptr,
	jlong accptr,
	jlong nttptr,
	jlong invnttptr,
	jlong numsel
)
{
	try {
		// note: this code is meant to insert a single chunk of data 
		// coming from a single database entry;
		// the data is ultimately stored in an accumulator, so
		// that it may be fully processed at the end of execution
		(void)_class;

		Ciphertext *query = reinterpret_cast<Ciphertext *>(ciphrptr);

		// accum points to the start of a sequence of ui128-pointers, 
		// with one such pointer per "computed-on-the-fly" subquery element  
		ui128 **accum = reinterpret_cast<ui128 **>(accptr);

		util::SmallNTTTables &invtable = *reinterpret_cast<util::SmallNTTTables *>(invnttptr);
		util::SmallNTTTables *ntttable = reinterpret_cast<util::SmallNTTTables *>(nttptr);

		// re-establish context, and then switch the modulus once, 
		// since a single switch has occurred by this point in the 
		// protocol, namely, immediately following partial query expansion
		shared_ptr<SEALContext> context =
			*reinterpret_cast<shared_ptr<SEALContext> *>(contextptr);
		auto context_data = context->context_data()->next_context_data();

		// compute the number of NTT slots in each targeted selector's band
		uint64_t sellen = 8192 / static_cast<uint64_t>(numsel);

		// construct a Plaintext object consisting of sellen-many coeffs; 
		// this plaintext shall be regarded as being in the NTT domain;
		// ultimately, it shall hold the periodically-extended data chunk
		Plaintext pl(sellen);

		// get native access to the java byte array called bytes;
		// the argument "NULL" means that bytes is copied, not pinned
		jbyte *p = env->GetByteArrayElements(bytes, NULL);

		// next, the bytes to which p points are partitioned into sets 
		// of size four, since the plaintext modulus holds up to four bytes;
		// thus, each such set corresponds to a single NTT coefficient,
		// and the (four) bytes within each such set effectively list 
		// the base-256 digits (from LSB to MSB) of the corresponding 
		// coefficient, so that casting by uint32_t reconstructs the 
		// coefficient
		uint32_t *data = reinterpret_cast<uint32_t *>(p);

		// create storage for the bit-reversal of a given index;
		// needed due to the NTT ordering
		size_t revi;

		// log_2(sellen)
		size_t logsize = 13 - util::get_power_of_two(numsel);

		// for each NTT coeff 
		for (size_t i = 0; i < sellen; i++){
		  // find its bit-reversal
		  revi = util::reverse_bits(i, logsize);
		  // store the four-byte coefficient in its
		  // corresponding NTT coefficient
		  pl.data()[i] = data[revi];
		}

		// transform from ntt domain to poly domain, inplace;
		// this is done since we'll next forward transform 
		// with respect to each prime factor of the ciphertext
		// modulus
		util::inverse_ntt_negacyclic_harvey(pl.data(), invtable);

		// forward transform to ntt domain w.r.t 
		// each prime factor in the ciphertext modulus;
                // note: pl is passed by reference, and it is 
                // resized by smallntt(), so that it can hold 
                // the ntt expansions w.r.t. each of the 
		// coeff_mod_count-many prime divisors of the 
		// current ciphertext modulus
		smallntt(pl, ntttable, sellen, context_data);

		// where was tmp used???????????????????????????????
		//Ciphertext tmp;

		// now plaintext-ciphertext multiply the plaintext pl 
                // with the index1-th "ntt-cached" subquery element,
		// and then add the result to the accumulator corresponding 
                // to the index2-nd "computed-on-the-fly" subquery element 
		PlainMulAccum(query[static_cast<size_t>(index1)], pl, accum[static_cast<size_t>(index2)]);

		// let it go
		env->ReleaseByteArrayElements(bytes, p, 0);
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
}

JNIEXPORT jbyteArray JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_getaccum(JNIEnv *env, jclass _class, jlong contextptr, jbyteArray pkdata, jlong accptr, jlong rkptr, jlong queryptr, jlong bitlen)
{
	try {
		//std::cerr << "Bit Length: " << bitlen << endl;

		//this code does the final processing and returns a ciphertext with
		//the result
		(void)_class;

		shared_ptr<SEALContext> context=
			*reinterpret_cast<shared_ptr<SEALContext> *>(contextptr);
	
		jbyte *accessor_one = env->GetByteArrayElements(pkdata, NULL);
                PublicKey pk = bytetopk(reinterpret_cast<uint8_t *>(accessor_one), context);
		env->ReleaseByteArrayElements(pkdata, accessor_one, 0);
		
		ui128 **accum=reinterpret_cast<ui128 **>(accptr);

		RelinKeys &rk=*reinterpret_cast<RelinKeys *>(rkptr);

		Ciphertext * query=reinterpret_cast<Ciphertext *>(queryptr);

		Evaluator eval(context);

		Ciphertext res1, res2, accumc;

		auto context_data=context->context_data()->next_context_data();

		uint32_t halflen = 1 << (bitlen / 2);
		uint32_t quarterlen = 1 << (bitlen / 4);

		// reduce the accumulator corresponding to the 
		// (0 * quarterlen^2 + 0 * quarterlen^3)-th 
		// "computed on-the-fly" subquery element
		AccumReduce(context_data, accum[0]);
		ToCipher(context, context_data->parms().parms_id(), &pk, accum[0], res1);
		eval.transform_from_ntt_inplace(res1);

		// compute (on-the-fly) the subquery element corresponding
		// to the identically zero top two base-quarterlen digits;
		// then, relinearize the result from a quadratic ciphertext
		// down to a linear ciphertext;
		// then, switch down the modulus once
		eval.multiply(query[(0 % quarterlen) + (2 * quarterlen)], query[(0 / quarterlen) + (3 * quarterlen)], res2);
		eval.relinearize_inplace(res2, rk);
		eval.mod_switch_to_next_inplace(res2);

		// multiply on-the-fly subquery element by
		// its accumulator, and then relinearize and 
		// then modulus switch once;
		// store the result in the Ciphertext object accumc 
		eval.multiply(res1, res2, accumc);
		eval.relinearize_inplace(accumc,rk);
		eval.mod_switch_to_next_inplace(accumc);

		// next, do the same sort of thing 
		// for the remaining "computed on-the-fly"
		// subquery elements, and always add the 
		// result to the Ciphertext object accumc, 
		// so that, at the loop's end, accumc holds 
		// an encryption of the entire column sum 
		for (size_t i=1; i < halflen; i++){

		  AccumReduce(context_data, accum[i]);
		  ToCipher(context, context_data->parms().parms_id(), &pk, accum[i], res1);
		  eval.transform_from_ntt_inplace(res1);

		  eval.multiply(query[(i % quarterlen) + (2 * quarterlen)], query[(i / quarterlen) + (3 * quarterlen)], res2);
		  eval.relinearize_inplace(res2, rk);
		  eval.mod_switch_to_next_inplace(res2);

		  eval.multiply_inplace(res1, res2);
		  eval.relinearize_inplace(res1, rk);
		  eval.mod_switch_to_next_inplace(res1);

		  eval.add_inplace(accumc, res1);
		  #ifdef TESTING
		  cout << reinterpret_cast<Decryptor *>(testdecryptptr)->invariant_noise_budget(
		    accumc ) << " is the noise budget" << endl;
		  #endif
		}

		// modulus switch the encrypted column sum accumc once
		eval.mod_switch_to_next_inplace(accumc);

		// allot room for the lowest modulus switched ciphertext
		jbyteArray toreturn = env->NewByteArray(FINALBYTES);
		jbyte *accessor_two = env->GetByteArrayElements(toreturn, NULL);
		ciphertobyte(accumc, reinterpret_cast<uint8_t *>(accessor_two));
		env->ReleaseByteArrayElements(toreturn, accessor_two, 0);

		return toreturn;
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
	return NULL;
}

JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_createevaluator(JNIEnv *env, jclass _class,jlong contextptr)
{
	try {
		(void)_class;
		(void)env;
		shared_ptr<SEALContext> context=
			*reinterpret_cast<shared_ptr<SEALContext> *>(contextptr);
		Evaluator *eval=new Evaluator(context);
		return reinterpret_cast<jlong>(eval);
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
	return 0;
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_destroyevaluator(JNIEnv *env, jclass _class, jlong evalptr)
{
	try {
		(void)_class;
		(void)env;
		if (reinterpret_cast<Evaluator *>(evalptr) != NULL){
		    delete reinterpret_cast<Evaluator *>(evalptr);
		}
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_destroyaccum(JNIEnv *env, jclass _class,jlong accumptr,jint bitlen)
{
	try {
		(void)_class;
		(void)env;
		uint64_t accumlen=1<<(bitlen/2);
		ui128 **accum = reinterpret_cast<ui128 **>(accumptr);
		if (accum != NULL){
		    for (size_t i = 0; i < accumlen; i++){
		        if (accum[i] != NULL){
			    delete[] accum[i];
		        }
		    }
		    delete[] accum;
		}
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_destroyquery(JNIEnv *env, jclass _class,jlong queryptr)
{
	try {
		(void)_class;
		(void)env;
		if (reinterpret_cast<Ciphertext *>(queryptr) != NULL){
		    delete[] reinterpret_cast<Ciphertext *>(queryptr);
		}
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
}

JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_createaccum(JNIEnv *env, jclass _class,jlong contextptr,jint bitlen)
{
	try {
		(void)_class;
		(void)env;
		shared_ptr<SEALContext> context=
			*reinterpret_cast<shared_ptr<SEALContext> *>(contextptr);
		auto context_data=context->context_data()->next_context_data();
		uint64_t accumlen=1<<(bitlen/2);
		ui128 **accum=new ui128*[accumlen];
		for(size_t i=0;i<accumlen;i++)
		{
			accum[i]=AccumCreate(context_data);
		}
		return reinterpret_cast<jlong>(accum);
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}

	return 0;
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_destroycontext(JNIEnv *env, jclass _class, jlong contextptr)
{
	try {
		(void)_class;
		(void)env;
		if (reinterpret_cast<shared_ptr<SEALContext> *>(contextptr) != NULL){
		    delete reinterpret_cast<shared_ptr<SEALContext> *>(contextptr);
		}
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
}

// the use here of the variable name "numsel" is questionable, 
// since it actually is a count of the number of ciphertexts 
// in a single query, which is 4 * 2^(hashBitSize/4), which 
// isn't generally the same as the number of selectors (i.e., "numsel")
JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_createquery(JNIEnv *env, jclass _class,jint numsel)
{
	try {
		(void)_class;
		(void)env;
		Ciphertext* ciphers=new Ciphertext[numsel];
		return reinterpret_cast<jlong>(ciphers);
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}

	return 0;
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_addquery(JNIEnv *env, jclass _class,jlong conptr,jlong queryptr,jint index,jbyteArray cipher)
{
	try {
		(void)_class;
		shared_ptr<SEALContext> context=
			*reinterpret_cast<shared_ptr<SEALContext> *>(conptr);
		//Evaluator &eval=*reinterpret_cast<Evaluator *>(evalptr);
		Ciphertext * query=reinterpret_cast<Ciphertext *>(queryptr);
		jbyte *c=env->GetByteArrayElements(cipher,NULL);
		Ciphertext ciph=bytetocipher(reinterpret_cast<uint8_t *>(c),context,0);
		ciph.is_ntt_form()=false;
		query[static_cast<size_t>(index)]=ciph;
		env->ReleaseByteArrayElements(cipher,c,0);
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
}

JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_createcontext(JNIEnv *env, jclass _class)
{
	try {
		(void)_class;
		(void)env;
		EncryptionParameters parms(scheme_type::BFV);
		parms.set_poly_modulus_degree(8192);
		parms.set_coeff_modulus(DefaultParams::coeff_modulus_128(8192));
		parms.set_plain_modulus(4295049217ull);
		auto context = SEALContext::Create(parms);
		shared_ptr<SEALContext> *contextptr=new shared_ptr<SEALContext>;
		*contextptr=context;
		#ifdef TESTING
		testcontext=contextptr;
		#endif
		return reinterpret_cast<jlong>(contextptr);
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}

	return 0;
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_resetaccum(JNIEnv *env, jclass _class,jlong contextptr,jlong accptr,jint bitlen)
{
	try {
		(void)env;
		(void)_class;
		shared_ptr<const SEALContext> context=
			*reinterpret_cast<shared_ptr<SEALContext> *>(contextptr);
		uint64_t num=1<<(bitlen/2);
		ui128 **accum=reinterpret_cast<ui128 **>(accptr);
		auto context_data=context->context_data()->next_context_data();
		for(size_t i=0;i<num;i++)
		{
			AccumReset(context_data,accum[i]);
		}
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
}

JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_createinvntt(JNIEnv *env, jclass _class,jlong contextptr,jlong numsel)
{
	try {
		(void)env;
		(void)_class;
		shared_ptr<const SEALContext> context=
			*reinterpret_cast<shared_ptr<SEALContext> *>(contextptr);
		util::SmallNTTTables* invtable=new util::SmallNTTTables;
		auto context_data=context->context_data();
		uint64_t plainmod=context_data->parms().plain_modulus().value();
		auto &table=context_data->plain_ntt_tables();
		uint64_t nsel=static_cast<uint64_t>(numsel);
		//not safe for values that aren't powers of 2
		uint64_t logsel=util::get_power_of_two(nsel);
		//hard coded for our dimension. For variable dimension modify this
		uint64_t root=table->get_from_root_powers(util::reverse_bits(nsel,13));
		invtable->generate(13-logsel,plainmod,root);
		return reinterpret_cast<jlong>(invtable);
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
	return 0;
}


JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_createntt(JNIEnv *env, jclass _class,jlong contextptr,jlong numsel)
{
	try {
		(void)env;
		(void)_class;
		shared_ptr<const SEALContext> context =
			*reinterpret_cast<shared_ptr<SEALContext> *>(contextptr);
		auto context_data=context->context_data();

		util::SmallNTTTables* nttptr = 
			new util::SmallNTTTables[context_data->parms().coeff_modulus().size()];

		uint64_t nsel=static_cast<uint64_t>(numsel);
		//not safe for values that aren't powers of 2
		uint64_t logsel=util::get_power_of_two(nsel);
		//hard coded for our dimension. For variable dimension modify this
		tablegen(nttptr,context_data,13-logsel);
		return reinterpret_cast<jlong>(nttptr);
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
	return 0;
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_destroyntt(JNIEnv *env, jclass _class, jlong nttptr)
{
	try {
		(void)env;
		(void)_class;
		if (reinterpret_cast<util::SmallNTTTables *>(nttptr) != NULL){
		    delete[] reinterpret_cast<util::SmallNTTTables *>(nttptr);
		}
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
}

// takes in jybteArray plain which is an 8*8192 long rep of a poly domain plaintext poly with little endian coeffs
JNIEXPORT jbyteArray JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_extractplain(JNIEnv *env, jclass _class,jlong contextptr,jlong numsel, jlong chunksize, jint index,jbyteArray plain)
{
	try {
		(void)_class;
		shared_ptr<const SEALContext> context=
			*reinterpret_cast<shared_ptr<SEALContext> *>(contextptr);
		auto context_data=context->context_data();
		auto &table=context_data->plain_ntt_tables();
		jbyte *p=env->GetByteArrayElements(plain,NULL);
		// converts bytestream to poly domain plaintext
		Plaintext pl=bytetoplain(reinterpret_cast<uint8_t *>(p),8192);
		// inplace forward ntt of plaintext
		util::ntt_negacyclic_harvey(pl.data(),*table.get());
		// the selector's total number of ntt slots
		uint64_t sellen=8192/static_cast<uint64_t>(numsel);
		// startpoint points to the LSB of the first overall ntt slot (i.e., uint64_t)
		// Note: incrementing startpoint traverses the bytes in the uint64_t values 
		// pointed to by pl.data() in LSB to MSB order
		uint8_t *startpoint=reinterpret_cast<uint8_t *>(pl.data());
		size_t startoff=sellen*static_cast<uint64_t>(index);
		// allocates 4 bytes for each of the selector's sellen-many ntt slots
		//jbyteArray toreturn = env->NewByteArray(sellen*4);
		jbyteArray toreturn = env->NewByteArray(static_cast<uint64_t>(chunksize));
		jbyte *accessor = env->GetByteArrayElements(toreturn,NULL);
		// bit-reverse of index i
		size_t revi;
		for (size_t i=0; i<(static_cast<uint64_t>(chunksize))/4; i++){
		  // bit-reverse the current ntt slot's index
		  revi=util::reverse_bits(startoff+i,13);
		  // unpack the four data chunk bytes from LSB to MSB
		  for (size_t j=0; j<4; j++){
		    accessor[4*i+j]=startpoint[8*revi+j];
		  }
		}
		size_t i = (static_cast<uint64_t>(chunksize))/4;
		// bit-reverse the current ntt slot's index
		revi=util::reverse_bits(startoff+i,13);
		for (size_t k=0; k<((static_cast<uint64_t>(chunksize))%4); k++){
                  accessor[4*i+k]=startpoint[8*revi+k];
		}
		env->ReleaseByteArrayElements(plain,p,0);
		env->ReleaseByteArrayElements(toreturn,accessor,0);
		return toreturn;
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}

	return NULL;
}
/*****************************************************************************************************/

// expands "half" of the query (i.e., creates the list of NTT-cached ciphertexts)
JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_createsubquery(JNIEnv *env, jclass _class,jlong evalptr,jlong queryptr,jlong relinptr,jint bitlen)
{
	try {
		(void)env;
		(void)_class;

		// e.g., if bitlen = 32,
		size_t sublen=1<<(bitlen/2); // then sublen = 2^(16)
		size_t quarterlen=1<<(bitlen/4); // and quarterlen = 2^(8)

		Evaluator &eval=*reinterpret_cast<Evaluator *>(evalptr);

		Ciphertext *query = reinterpret_cast<Ciphertext *>(queryptr);

		RelinKeys &rk=*reinterpret_cast<RelinKeys*>(relinptr);

		Ciphertext *subquery=new Ciphertext[sublen];

		for (size_t i=0; i < sublen; i++){
		  eval.multiply(query[(i % quarterlen) + (0 * quarterlen)], query[(i / quarterlen) + (1 * quarterlen)], subquery[i]);
		  eval.relinearize_inplace(subquery[i], rk);
		  eval.mod_switch_to_next_inplace(subquery[i]);
		  eval.transform_to_ntt_inplace(subquery[i]);

		  /*
		  if ((i % 1000) == 0){
		    printf("	formed NTT-cached ciphertext %lu of %lu\n", i + 1, sublen);
		  }
		  */

		}
		return reinterpret_cast<jlong>(subquery);
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
	return 0;
}
/***********************************************************************************************************/

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_destroysubquery(JNIEnv *env, jclass _class,jlong subqueryptr)
{
	try {
		(void)env;
		(void)_class;
		if (reinterpret_cast<Ciphertext *>(subqueryptr) != NULL){
		    delete[] reinterpret_cast<Ciphertext *>(subqueryptr);
		}
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
}

JNIEXPORT jbyteArray JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_getrk(JNIEnv *env, jclass _class,jlong keyptr)
{
	try {
		(void)_class;
		(void)env;
		KeyGenerator &keygen=*reinterpret_cast<KeyGenerator *>(keyptr);
		RelinKeys rk=keygen.relin_keys(60);
		PublicKey public_key=keygen.public_key();
		jbyteArray toreturn = env->NewByteArray(4*CIPHERBYTES);
		jbyte * accessor = env->GetByteArrayElements(toreturn,NULL);
		rktobyte(rk,reinterpret_cast<uint8_t *>(accessor));
		env->ReleaseByteArrayElements(toreturn,accessor,0);
		return toreturn;
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
	return NULL;
}

JNIEXPORT long JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_createrk(JNIEnv *env, jclass _class,jbyteArray rkdata,jlong conptr)
{
	try {
		(void)_class;
		RelinKeys *rk=new RelinKeys();
		shared_ptr<SEALContext> context=
			*reinterpret_cast<shared_ptr<SEALContext> *>(conptr);
		jbyte *accessor = env->GetByteArrayElements(rkdata,NULL);
		*rk=bytetork(reinterpret_cast<uint8_t *>(accessor),context);
		env->ReleaseByteArrayElements(rkdata,accessor,0);
		return reinterpret_cast<jlong>(rk);
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}
	return 0;
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_seal_SEALCpp_destroyrk(JNIEnv *env, jclass _class,jlong rkptr)
{
	try {
		(void)env;
		(void)_class;
		if (reinterpret_cast<RelinKeys *>(rkptr) != NULL){
		    delete reinterpret_cast<RelinKeys *>(rkptr);
		}
	}
	catch (const std::exception &exc)
	{
		std::cerr << "C++ error: \"" << exc.what() << "\"" << endl;
	}
	catch(...)
	{
		std::cerr << "Unknown C++ error." << endl;
	}

}
