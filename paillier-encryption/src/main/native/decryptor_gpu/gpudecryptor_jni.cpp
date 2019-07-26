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

#include "gpudecryptor.h"
#include <jni.h>
#include <gmp.h>

#include <thread>
#include <stdexcept>

static const char CFG_BUSY_POLICY[] = "paillier.gpu.libdecryptor.busy.policy";

extern pool_t<device_t> device_pool;

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
 * Method:    gpuDecryptorInitialize
 * Signature: (Ljava/util/Map;)Z
 */
JNIEXPORT jboolean JNICALL Java_org_enquery_encryptedquery_encryption_paillier_PaillierCryptoScheme_gpuDecryptorInitialize
  (JNIEnv *env, jobject obj, jobject cfg) {
    std::thread::id this_id = std::this_thread::get_id();
    COUT_BEGIN;
    std::cout << "thread " << this_id << " : ";
    std::cout << "gpuDecryptorInitialize()" << std::endl;
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
	std::string policyString = std::string(paramBytes, paramLen);
	env->ReleaseStringUTFChars(param, paramBytes);
	COUT_BEGIN;
	std::cout << "thread " << this_id << " : ";
	std::cout << CFG_BUSY_POLICY << "=" << policyString << std::endl;
	COUT_END;
	policy = busyPolicyFromString(policyString, DEFAULT_BUSY_POLICY);
    } else {
	COUT_BEGIN;
	std::cout << "thread " << this_id << " : ";
	std::cout << "using default busy policy" << std::endl;
	COUT_END;
	policy = DEFAULT_BUSY_POLICY;
    }    
    set_busy_policy(policy);

    return JNI_TRUE;
}

/********************
 * Class:     org_enquery_encryptedquery_encryption_paillier_GPUDecryptor
 * Method:    createContext
 * Signature: ([B[B[B[B)J
 *
 * Function:  (JNI) createContext
 *
 * Allocates and initializes a decryption context object, given a
 *   Paillier private key.
 *
 * Arguments:
 *     jPBytes: Java byte array contains the bytes of p (big endian order)
 *     jQBytes: Java byte array contains the bytes of q (big endian order)
 *     jP1Bytes: Java byte array contains the bytes of p1 (big endian order)
 *     jQ1Bytes: Java byte array contains the bytes of q1 (big endian order)
 *
 * Returns a pointer to the created object, or NULL if an error occurred.
 */
JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_encryption_paillier_GPUDecryptor_createContext
  (JNIEnv *env, jobject obj, jbyteArray jPBytes, jbyteArray jQBytes, jbyteArray jP1Bytes, jbyteArray jQ1Bytes)
{
    try {
	mpz_t p, q, p1, q1;
	mpz_inits(p, q, p1, q1, NULL);// TODO: use C++ version for cleanup in case of exception
	jsize num_bytes;
	jbyte *bytes;

#define MPZ_FROM_BYTES(z, jZBytes)				\
	num_bytes = env->GetArrayLength(jZBytes);		\
	bytes = env->GetByteArrayElements(jZBytes, NULL);	\
	mpz_from_be(z, (uint8_t*)bytes, num_bytes);		\
	env->ReleaseByteArrayElements(jZBytes, bytes, JNI_ABORT);

	MPZ_FROM_BYTES(p, jPBytes);
	MPZ_FROM_BYTES(q, jQBytes);
	MPZ_FROM_BYTES(p1, jP1Bytes);
	MPZ_FROM_BYTES(q1, jQ1Bytes);
#undef MPZ_FROM_BYTES

	handle_t h_context = gpudecryptor_new(p, q, p1, q1);
	mpz_clears(p, q, p1, q1, NULL);
	return (jlong)h_context;
    } catch (const std::exception &exc) {
	std::thread::id this_id = std::this_thread::get_id();
	std::cout << "thread " << this_id << " : ";
	std::cout << exc.what() << std::endl;
	return 0;
    } catch (...) {
	std::thread::id this_id = std::this_thread::get_id();
	std::cout << "thread " << this_id << " : ";
	std::cout << "unknown exception occurred" << std::endl;
	return 0;
    }
}

/********************
 * Class:     org_enquery_encryptedquery_encryption_paillier_GPUDecryptor
 * Method:    destroyContext
 * Signature: (J)Z
 *
 * Function:  (JNI) destroyContext
 *
 * Deallocates a given decryption context object.
 *
 * Arguments:
 *     jHandle:  a pointer to the decryption context object
 *
 * Returns JNI_TRUE for success, or JNI_FALSE if an error occurred.
 */
JNIEXPORT jboolean JNICALL Java_org_enquery_encryptedquery_encryption_paillier_GPUDecryptor_destroyContext
  (JNIEnv *env, jobject obj, jlong jHandle)
{
    try {
	jboolean success = 0;
	if (jHandle) {
	    gpudecryptor_delete((handle_t)jHandle);
	    success = JNI_TRUE;
	}
	return success;
    } catch (const std::exception &exc) {
	std::thread::id this_id = std::this_thread::get_id();
	std::cout << "thread " << this_id << " : ";
	std::cout << exc.what() << std::endl;
	return JNI_FALSE;
    } catch (...) {
	std::thread::id this_id = std::this_thread::get_id();
	std::cout << "thread " << this_id << " : ";
	std::cout << "unknown exception occurred" << std::endl;
	return JNI_FALSE;
    }
}

/********************
 * Class:     org_enquery_encryptedquery_encryption_paillier_GPUDecryptor
 * Method:    decryptBatch
 * Signature: (JLjava/nio/ByteBuffer;I)Z
 *
 * Function:  (JNI) decryptBatch
 * 
 * Decrypts a list of Paillier ciphertexts.  The input ciphertexts are
 * passed in via a Java NIO direct buffer, with each ciphertext value
 * stored as C consecutive bytes, where C = ctx->ciphertext_byte_size,
 * in big endian order.  The output plaintexts will be returned via
 * the same direct buffer, overwriting the beginning of the ciphertext
 * data.  Each plaintext will be stored as P consecutive bytes, where
 * P = ctx->plaintext_byte_size, in big endian order.
 *
 * Arguments:
 *     jIobuf:  a Java direct buffer containing the bytes of the ciphertext values to be decrypted
 *     jBatchSize:  the number of ciphertext values to be decrypted
 *
 * Returns JNI_TRUE for success, or JNI_FALSE if an error occurred.
 */
JNIEXPORT jboolean JNICALL Java_org_enquery_encryptedquery_encryption_paillier_GPUDecryptor_decryptBatch
  (JNIEnv *env, jobject obj, jlong jHandle, jobject jIobuf, jint jBatchSize)
{
    std::thread::id this_id = std::this_thread::get_id();
    handle_t h_context = (handle_t)jHandle;
    if (HANDLE_ERROR == h_context) {
	return JNI_FALSE;
    }
    try {
	uint8_t *buf = (uint8_t*)env->GetDirectBufferAddress(jIobuf);
	int num_ciphs = (int)jBatchSize;
	int istatus = gpudecryptor_decrypt_batch(h_context, buf, num_ciphs);
	if (istatus == 4) {
	    // error: no available GPU context under RP_ABORT policy
            COUT_BEGIN;
            std::cout << "thread " << this_id << " : ";
            std::cout << "error: no available GPU context under RP_ABORT policy" << std::endl;
            COUT_END;
            jclass exClass;
            const char *className = "java/util/concurrent/RejectedExecutionException";
            const char *message= "all GPUs are busy";
            exClass = env->FindClass(className);
            if (exClass == NULL) {
                COUT_BEGIN;
                std::cout << "thread " << this_id << " : ";
                std::cout << "RejectionExecutionException class not found" << std::endl;
                COUT_END;
                return JNI_FALSE;
            }
            jint throw_status = env->ThrowNew(exClass, message);
            if (throw_status != 0) {
                COUT_BEGIN;
                std::cout << "thread " << this_id << " : ";
                std::cout << "throw failed" << std::endl;
                COUT_END;
            }
            return JNI_FALSE;
        }
	return istatus ? JNI_FALSE : JNI_TRUE;
    } catch (const std::exception &exc) {
	std::thread::id this_id = std::this_thread::get_id();
	std::cout << "thread " << this_id << " : ";
	std::cout << exc.what() << std::endl;
	return JNI_FALSE;
    } catch (...) {
	std::thread::id this_id = std::this_thread::get_id();
	std::cout << "thread " << this_id << " : ";
	std::cout << "unknown exception occurred" << std::endl;
	return JNI_FALSE;
    }
}


} // extern "C"
