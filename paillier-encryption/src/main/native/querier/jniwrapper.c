/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018  EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "org_enquery_encryptedquery_encryption_paillier_EncryptQueryFixedBaseWithJNITaskFactory.h"
#include "querygen.h"
#include <gmp.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include <assert.h>


JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_encryption_paillier_EncryptQueryFixedBaseWithJNITaskFactory_newContext
(JNIEnv *env, jobject thisObj, jbyteArray jPBytes, jbyteArray jGeneratorPSquaredBytes, jbyteArray jQBytes, jbyteArray jGeneratorQSquaredBytes, jint jPrimeBitLength, jint jWindowSize, jint jNumWindows)
{
  mpz_t p, q;
  mpz_t gen_p2, gen_q2;
  encquery_ctx_t *ctx;

  jsize jPBytesLength = (*env)->GetArrayLength(env, jPBytes);
  jsize jQBytesLength = (*env)->GetArrayLength(env, jQBytes);
  jbyte *jpbytes = (*env)->GetByteArrayElements(env, jPBytes, NULL);
  jbyte *jqbytes = (*env)->GetByteArrayElements(env, jQBytes, NULL);
  jsize jgenp2len = (*env)->GetArrayLength(env, jGeneratorPSquaredBytes);
  jsize jgenq2len = (*env)->GetArrayLength(env, jGeneratorQSquaredBytes);
  jbyte *jgenp2bytes = (*env)->GetByteArrayElements(env, jGeneratorPSquaredBytes, NULL);
  jbyte *jgenq2bytes = (*env)->GetByteArrayElements(env, jGeneratorQSquaredBytes, NULL);

  //printf ("jPBytesLength: %d\n", (int)jPBytesLength);
  //printf ("jQBytesLength: %d\n", (int)jQBytesLength);
  //printf ("jgenp2len: %d\n", (int)jgenp2len);
  //printf ("jgenq2len: %d\n", (int)jgenq2len);
  
  mpz_init(p);
  mpz_init(q);
  mpz_init(gen_p2);
  mpz_init(gen_q2);
  mpz_import(p, jPBytesLength, 1, 1, -1, 0, jpbytes);
  mpz_import(q, jQBytesLength, 1, 1, -1, 0, jqbytes);
  mpz_import(gen_p2, jgenp2len, 1, 1, -1, 0, jgenp2bytes);
  mpz_import(gen_q2, jgenq2len, 1, 1, -1, 0, jgenq2bytes);

  //gmp_printf ("imported p: %Zd\n", p);
  //gmp_printf ("imported q: %Zd\n", q);
  //gmp_printf ("imported generator mod p^2: %Zd\n", gen_p2);
  //gmp_printf ("imported generator mod q^2: %Zd\n", gen_q2);

  //printf ("jWindowSize: %d\n", (int)jWindowSize);
  //printf ("jNumWindows: %d\n", (int)jNumWindows);

  ctx = encquery_ctx_new(p, gen_p2, q, gen_q2, (int)jWindowSize, (int)jNumWindows);

  mpz_clear(p);
  mpz_clear(q);
  mpz_clear(gen_p2);
  mpz_clear(gen_q2);

  (*env)->ReleaseByteArrayElements(env, jPBytes, jpbytes, JNI_ABORT);
  (*env)->ReleaseByteArrayElements(env, jQBytes, jqbytes, JNI_ABORT);
  (*env)->ReleaseByteArrayElements(env, jGeneratorPSquaredBytes, jgenp2bytes, JNI_ABORT);
  (*env)->ReleaseByteArrayElements(env, jGeneratorQSquaredBytes, jgenq2bytes, JNI_ABORT);

  return (jlong) ctx;
}


JNIEXPORT jbyteArray JNICALL Java_org_enquery_encryptedquery_encryption_paillier_EncryptQueryFixedBaseWithJNITaskFactory_encrypt
  (JNIEnv *env, jobject thisObj, jlong hContext, jint jBitIndex, jbyteArray jrpBytes, jbyteArray jrqBytes)
{
  mpz_t ans;
  encquery_ctx_t *ctx = (encquery_ctx_t*) hContext;
  jbyte *jrpbytes = (*env)->GetByteArrayElements(env, jrpBytes, NULL);
  jbyte *jrqbytes = (*env)->GetByteArrayElements(env, jrqBytes, NULL);
  jsize jrpBytesLength = (*env)->GetArrayLength(env, jrpBytes);
  jsize jrqBytesLength = (*env)->GetArrayLength(env, jrqBytes);
  jbyte *ansbytes;
  size_t ansbyteslength;
  jbyteArray ansArray;
  size_t tmpsize;
  //jobject ansBigInt;

  //printf ("from C: Java_EncryptQueryWithJNI_encrypt()\n");

  mpz_init(ans);

  encquery_encrypt(ctx, ans, (int)jBitIndex, (unsigned char*)jrpbytes, (int)jrpBytesLength, (unsigned char*)jrqbytes, (int)jrqBytesLength);
  //gmp_printf ("from C: ans = %Zd\n", ans);
  
  ansbyteslength = mpz_sizeinbase(ans, 256);
  //printf ("ansbyteslength: %d\n", (int)ansbyteslength);

  ansArray = (*env)->NewByteArray(env, ansbyteslength);
  if (NULL == ansArray) {
    char msg[100];
    snprintf(msg, sizeof(msg), "NewByteArray() returned NULL (ansbyteslength = %ld)", ansbyteslength);
    msg[sizeof(msg)-1] = 0;
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/RuntimeException"), msg);
  }
  ansbytes = (*env)->GetByteArrayElements(env, ansArray, NULL);
  assert (NULL != ansbytes);

  mpz_export(ansbytes, &tmpsize, 1, 1, -1, 0, ans);
  assert (tmpsize == ansbyteslength);

  (*env)->ReleaseByteArrayElements(env, jrpBytes, jrpbytes, JNI_ABORT);
  (*env)->ReleaseByteArrayElements(env, jrqBytes, jrqbytes, JNI_ABORT);
  (*env)->ReleaseByteArrayElements(env, ansArray, ansbytes, 0);
  
  mpz_clear(ans);

  //printf ("from C: returning ansArray\n");
  return ansArray;
}

