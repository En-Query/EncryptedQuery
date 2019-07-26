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

#include <jni.h>

#include "gpucolproc.h"

#define NDEBUG
#include <cassert>
#include <cstdint>
#include <iostream>
#include <map>
#include <mutex>
#include <thread>
#include <sstream>

using namespace std;

static std::mutex create_query_mutex;
extern std::mutex query_mutex;
extern std::map<handle_t,query_t*> queries;
extern pool_t<device_t> device_pool;

static const char CFG_BUSY_POLICY[] = "paillier.gpu.libresponder.busy.policy";
static const char CFG_LOG_FILE[]    = "paillier.gpu.libresponder.log.file";

extern "C" {

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    initialize_native_library();
    return JNI_VERSION_1_1;
}

void JNI_OnUnload(JavaVM *vm, void *reserved) {
    close_native_library();
}

/*
 * Class:     org_enquery_encryptedquery_encryption_paillier_PaillierCryptoScheme
 * Method:    gpuResponderInitialize
 * Signature: (Ljava/util/Map;)Z
 */
JNIEXPORT jboolean JNICALL Java_org_enquery_encryptedquery_encryption_paillier_PaillierCryptoScheme_gpuResponderInitialize
  (JNIEnv *env, jobject obj, jobject cfg) {
    std::thread::id this_id = std::this_thread::get_id();
    COUT_BEGIN;
    cout << "thread " << this_id << " : ";
    cout << "gpuResponderInitialize()" << endl;
    COUT_END;
    jclass clsMap = env->FindClass("java/util/Map");
    if (NULL == clsMap) return JNI_FALSE;
    jmethodID midGet = env->GetMethodID(clsMap, "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
    if (NULL == midGet) return JNI_FALSE;

    jstring param;
    jsize paramLen;
    const char *paramBytes;

    param = (jstring)env->CallObjectMethod(cfg, midGet, env->NewStringUTF(CFG_BUSY_POLICY));
    policy_t policy;
    if (NULL != param) {
	paramLen = env->GetStringUTFLength(param);
	paramBytes = env->GetStringUTFChars(param, JNI_FALSE);
	string policyString = string(paramBytes, paramLen);
	env->ReleaseStringUTFChars(param, paramBytes);
	COUT_BEGIN;
	cout << "thread " << this_id << " : ";
	cout << CFG_BUSY_POLICY << "=" << policyString << endl;
	COUT_END;
	policy = busyPolicyFromString(policyString, DEFAULT_BUSY_POLICY);
    } else {
	COUT_BEGIN;
	cout << "thread " << this_id << " : ";
	cout << "using default busy policy" << endl;
	COUT_END;
	policy = DEFAULT_BUSY_POLICY;
    }    
    set_busy_policy(policy);
    
    param = (jstring)env->CallObjectMethod(cfg, midGet, env->NewStringUTF(CFG_LOG_FILE));
    if (NULL != param) {
	paramLen = env->GetStringUTFLength(param);
	paramBytes = env->GetStringUTFChars(param, JNI_FALSE);
	string logFilePath = string(paramBytes, paramLen);
	env->ReleaseStringUTFChars(param, paramBytes);
	COUT_BEGIN;
	cout << "thread " << this_id << " : ";
	cout << "logFilePath = " << logFilePath << endl;
	logging = true;
	log_file_prefix = logFilePath;
	COUT_END;
    }
    return JNI_TRUE;
}

/*
 * Class:     org_enquery_encryptedquery_encryption_paillier_PaillierCryptoScheme
 * Method:    gpuResponderLoadQuery
 * Signature: (Ljava/lang/String;I[BILjava/util/Map;)J
 */
JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_encryption_paillier_PaillierCryptoScheme_gpuResponderLoadQuery
  (JNIEnv *env, jobject obj, jstring queryId, jint modulusBitSize, jbyteArray jNBytes, jint hashBitSize, jobject qeltsMap)
{
    std::thread::id this_id = std::this_thread::get_id();
    //COUT_BEGIN;
    //cout << "gpuResponderLoadQuery()" << endl;
    //COUT_END;
    try {

	// only one thread can run this function at a time
	std::lock_guard<std::mutex> guard(create_query_mutex);

	// convert query ID to C++ std::string
	jsize queryIdLength = env->GetStringUTFLength(queryId);
	const char *queryIdBytes = env->GetStringUTFChars(queryId, JNI_FALSE);
	string queryIdString(queryIdBytes, queryIdLength);
	env->ReleaseStringUTFChars(queryId, queryIdBytes);
	
	// if query already exists, return handle
	{
	    std::lock_guard<std::mutex> guard2(query_mutex);
	    for (auto it=queries.begin(); it != queries.end(); it++) {
		query_t *query = it->second;
		if (query && query->uuid == queryIdString) {
		    // found!
		   // COUT_BEGIN;
		   // cout << "thread " << this_id << " : ";
		   // cout << "query for ID " << queryIdString << " already exists, new reference count = " << query->ref_count << endl;
		   // COUT_END;
		    query->ref_count++;
		    return (jlong)it->first;
		}
	    }
	}
	COUT_BEGIN;
	cout << "thread " << this_id << " : ";
	cout << "creating new query for ID = " << queryIdString << endl;
	COUT_END;

	jlong ret = (jlong)0;
	jclass clsInteger = env->FindClass("java/lang/Integer");
	jmethodID midInteger = env->GetMethodID(clsInteger, "<init>", "(I)V");
	jclass clsMap = env->FindClass("java/util/Map");
	jmethodID midGet = env->GetMethodID(clsMap, "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
	jclass clsPaillierCipherText = env->FindClass("org/enquery/encryptedquery/encryption/paillier/PaillierCipherText");
	jmethodID midGetValue = env->GetMethodID(clsPaillierCipherText, "getValue", "()Ljava/math/BigInteger;");
	jclass clsBigInteger = env->FindClass("java/math/BigInteger");
	jmethodID midToByteArray = env->GetMethodID(clsBigInteger, "toByteArray", "()[B");
	
#ifdef GPURESPONDER_DEBUG
	cout << "clsInteger = " << clsInteger << endl;
	cout << "midInteger = " << midInteger << endl;
	cout << "clsMap = " << clsMap << endl;
	cout << "midGet = " << midGet << endl;
	cout << "clsPaillierCipherText = " << clsPaillierCipherText << endl;
	cout << "midGetValue = " << midGetValue << endl;
	cout << "clsBigInteger = " << clsBigInteger << endl;
	cout << "midToByteArray = " << midToByteArray << endl;
#endif
	
	if (clsInteger == NULL) return ret;
	if (midInteger == NULL) return ret;
	if (clsMap == NULL) return ret;
	if (midGet == NULL) return ret;
	if (clsPaillierCipherText == NULL) return ret;
	if (midGetValue == NULL) return ret;
	if (clsBigInteger == NULL) return ret;
	if (midToByteArray == NULL) return ret;
	
	const size_t w = ((size_t)(uint32_t)modulusBitSize + 31) / 32;
	size_t num_qelts = (size_t)1 << (int)hashBitSize;
	
	jsize num_N_bytes = env->GetArrayLength(jNBytes);
	jbyte *N_bytes = env->GetByteArrayElements(jNBytes, NULL);
	mpz_t N;
	mpz_init(N);
	mpz_import(N, num_N_bytes, 1, 1, 0, 0, N_bytes);
#ifdef GPUREPONDER_DEBUG
	cout << "modulusBitSize = " << modulusBitSize << endl;
	cout << "w = " << w << endl;
	gmp_printf ("N = %Zd\n", N);
	assert (modulusBitSize == mpz_sizeinbase(N,2));
#endif
	env->ReleaseByteArrayElements(jNBytes, N_bytes, JNI_ABORT);
	
	handle_t h_query = create_query(queryIdString, N, num_qelts);
	mpz_clear(N);
	if (h_query == 0) return ret;
	query_t *query = lookup_query(h_query);
	if (query == NULL) return ret;
	COUT_BEGIN;
	cout << "thread " << this_id << " : ";
	cout << "new query created, reference count = " << query->ref_count << endl;
	COUT_END;
	
	mpz_t qelt;
	mpz_init(qelt);
	for (size_t i = 0; i < num_qelts; i++) {
	    jobject key = env->NewObject(clsInteger, midInteger, i);
	    jobject ciphertext = env->CallObjectMethod(qeltsMap, midGet, key);
	    // assuming ciphertext of type CipherText can be used directly as
	    // an object of type PaillierCipherText
	    jobject bi = env->CallObjectMethod(ciphertext, midGetValue);
	    jbyteArray qeltByteArray = (jbyteArray) env->CallObjectMethod(bi, midToByteArray);
	    jsize num_bytes = env->GetArrayLength(qeltByteArray);
	    jbyte *bytes = env->GetByteArrayElements(qeltByteArray, NULL);
	    mpz_import(qelt, num_bytes, 1, 1, 0, 0, bytes);
	    
	    query->set_qelt(i, qelt);
	    
	    env->ReleaseByteArrayElements(qeltByteArray, bytes, JNI_ABORT);
	    env->DeleteLocalRef(qeltByteArray);
	    env->DeleteLocalRef(bi);
	    env->DeleteLocalRef(ciphertext);
	    env->DeleteLocalRef(key);
	}
	mpz_clear(qelt);
	
	ret = (jlong)h_query;
	return ret;
    } catch (const std::exception &exc) {
	cout << "thread " << this_id << " : ";
	cout << exc.what() << endl;
	return 0;
    } catch (...) {
	cout << "thread " << this_id << " : ";
	cout << "unknown exception occurred" << endl;
	return 0;
    }
}

/*
 * Class:     org_enquery_encryptedquery_encryption_paillier_PaillierCryptoScheme
 * Method:    gpuResponderUnloadQuery
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_org_enquery_encryptedquery_encryption_paillier_PaillierCryptoScheme_gpuResponderUnloadQuery
  (JNIEnv *env, jobject obj, jlong hQuery)
{
    try {
	return (jboolean) remove_query((handle_t)hQuery);
    } catch (const std::exception &exc) {
	std::thread::id this_id = std::this_thread::get_id();
	cout << "thread " << this_id << " : ";
	cout << exc.what() << endl;
	return JNI_FALSE;
    } catch (...) {
	std::thread::id this_id = std::this_thread::get_id();
	cout << "thread " << this_id << " : ";
	cout << "unknown exception occurred" << endl;
	return JNI_FALSE;
    }
}

////////////////////////////////////////////////////////////

/*
 * Class:     org_enquery_encryptedquery_encryption_paillier_GPUColumnProcessor
 * Method:    createColproc
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_encryption_paillier_GPUColumnProcessor_createColproc
  (JNIEnv *env, jobject obj, jlong hQuery)
{
    try {
	handle_t h_colproc = create_colproc((handle_t) hQuery);
	return (jlong)h_colproc;
    } catch (const std::exception &exc) {
	std::thread::id this_id = std::this_thread::get_id();
	cout << "thread " << this_id << " : ";
	cout << exc.what() << endl;
	return 0;
    } catch (...) {
	std::thread::id this_id = std::this_thread::get_id();
	cout << "thread " << this_id << " : ";
	cout << "unknown exception occurred" << endl;
	return 0;
    }
}

/*
 * Class:     org_enquery_encryptedquery_encryption_paillier_GPUColumnProcessor
 * Method:    colprocInsertChunk
 * Signature: (JII)Z
 */
JNIEXPORT jboolean JNICALL Java_org_enquery_encryptedquery_encryption_paillier_GPUColumnProcessor_colprocInsertChunk
  (JNIEnv *env, jobject obj, jlong hColproc, jint rowIndex, jint chunk)
{
#ifdef GPURESPONDER_DEBUG
    std::thread::id this_id = std::this_thread::get_id();
    COUT_BEGIN;
    cout << "thread " << this_id << " : ";
    cout << "colprocInsertChunk(hColproc=" << hColproc << ",row=" << rowIndex << ",chunk=" << chunk << ")" << endl;
    COUT_END;
#endif
    try {
	return (jboolean)colproc_insert_chunk((handle_t)hColproc, (size_t)(uint32_t)rowIndex, (uint32_t)chunk);
    } catch (const std::exception &exc) {
	std::thread::id this_id = std::this_thread::get_id();
	cout << "thread " << this_id << " : ";
	cout << exc.what() << endl;
	return JNI_FALSE;
    } catch (...) {
	std::thread::id this_id = std::this_thread::get_id();
	cout << "thread " << this_id << " : ";
	cout << "unknown exception occurred" << endl;
	return JNI_FALSE;
    }
}

/*
 * Class:     org_enquery_encryptedquery_encryption_paillier_GPUColumnProcessor
 * Method:    colprocComputeAndClear
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL Java_org_enquery_encryptedquery_encryption_paillier_GPUColumnProcessor_colprocComputeAndClear
  (JNIEnv *env, jobject obj, jlong hColproc)
{
    std::thread::id this_id = std::this_thread::get_id();
#ifdef GPURESPONDER_DEBUG
    COUT_BEGIN;
    cout << "thread " << this_id << " : ";
    cout << "colprocComputeAndClear(hColproc=" << hColproc << ")" << endl;
    COUT_END;
#endif
    jbyteArray ansArray = NULL;
    mpz_t result;
    mpz_init(result);
    bool status;
    try {
	int istatus = colproc_compute_and_clear((handle_t) hColproc, result);
	if (istatus == 4) {
	    // error: no available GPU context under RP_ABORT policy
            COUT_BEGIN;
            cout << "thread " << this_id << " : ";
            cout << "error: no available GPU context under RP_ABORT policy" << endl;
            COUT_END;
            jclass exClass;
            const char *className = "java/util/concurrent/RejectedExecutionException";
            const char *message= "all GPUs are busy";
            exClass = env->FindClass(className);
            if (exClass == NULL) {
                COUT_BEGIN;
                cout << "thread " << this_id << " : ";
                cout << "RejectionExecutionException class not found" << endl;
                COUT_END;
                return ansArray;
            }
            jint throw_status = env->ThrowNew(exClass, message);
            if (throw_status != 0) {
                COUT_BEGIN;
                cout << "thread " << this_id << " : ";
                cout << "throw failed" << endl;
                COUT_END;
            }
            return ansArray;
        }
        status = istatus ? false : true;
    } catch (const std::exception &exc) {
	std::thread::id this_id = std::this_thread::get_id();
	cout << "thread " << this_id << " : ";
	cout << exc.what() << endl;
	return ansArray;
    } catch (...) {
	std::thread::id this_id = std::this_thread::get_id();
	cout << "thread " << this_id << " : ";
	cout << "unknown exception occurred" << endl;
	return ansArray;
    }
    if (status) {
	size_t ansbyteslength = mpz_sizeinbase(result, 256);
	ansArray = env->NewByteArray(ansbyteslength);
	if (NULL == ansArray) {
	    std::stringstream ss;
	    ss << "NewByteArray() returned NULL (ansbyteslength = " << ansbyteslength << ")";
	    std::string s = ss.str();
	    env->ThrowNew(env->FindClass("java/lang/RuntimeException"), s.c_str());
	}
	jbyte *ansbytes = env->GetByteArrayElements(ansArray, NULL);
	size_t tmpsize;
	mpz_export(ansbytes, &tmpsize, 1, 1, -1, 0, result);
	assert (tmpsize == ansbyteslength);
	env->ReleaseByteArrayElements(ansArray, ansbytes, 0);
    }
    mpz_clear(result);
    return ansArray;
}

/*
 * Class:     org_enquery_encryptedquery_encryption_paillier_GPUColumnProcessor
 * Method:    colprocClear
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_org_enquery_encryptedquery_encryption_paillier_GPUColumnProcessor_colprocClear
  (JNIEnv *env, jobject obj, jlong hColproc)
{
    try {
	return (jboolean)colproc_clear((handle_t)hColproc);
    } catch (const std::exception &exc) {
        std::thread::id this_id = std::this_thread::get_id();
	cout << "thread " << this_id << " : ";
	cout << exc.what() << endl;
	return JNI_FALSE;
    } catch (...) {
        std::thread::id this_id = std::this_thread::get_id();
	cout << "thread " << this_id << " : ";
	cout << "unknown exception occurred" << endl;
	return JNI_FALSE;
    }	
}

/*
 * Class:     org_enquery_encryptedquery_encryption_paillier_GPUColumnProcessor
 * Method:    colprocRemove
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_org_enquery_encryptedquery_encryption_paillier_GPUColumnProcessor_colprocRemove
  (JNIEnv *env, jobject obj, jlong hColproc)
{
    try {
	return (jboolean)remove_colproc((handle_t)hColproc);
    } catch (const std::exception &exc) {
	std::thread::id this_id = std::this_thread::get_id();
	cout << "thread " << this_id << " : ";
	cout << exc.what() << endl;
	return JNI_FALSE;
    } catch (...) {
	std::thread::id this_id = std::this_thread::get_id();
	cout << "thread " << this_id << " : ";
	cout << "unknown exception occurred" << endl;
	return JNI_FALSE;
    }
}

} // extern "C"
