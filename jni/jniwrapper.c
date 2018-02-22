/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

#include "jniwrapper.h"
#include "querygen.h"
#include <gmp.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include <assert.h>

// https://stackoverflow.com/questions/2093112/why-i-should-not-reuse-a-jclass-and-or-jmethodid-in-jni


JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_querier_wideskies_encrypt_EncryptQueryFixedBaseWithJNITaskFactory_newContext
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

  return (jlong) ctx;
}


JNIEXPORT jbyteArray JNICALL Java_org_enquery_encryptedquery_querier_wideskies_encrypt_EncryptQueryFixedBaseWithJNITask_encrypt
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
  assert (NULL != ansArray);
  ansbytes = (*env)->GetByteArrayElements(env, ansArray, NULL);
  assert (NULL != ansbytes);

  mpz_export(ansbytes, &tmpsize, 1, 1, -1, 0, ans);

  assert (tmpsize == ansbyteslength);
  (*env)->ReleaseByteArrayElements(env, ansArray, ansbytes, JNI_COMMIT);

//  (*env)->DeleteLocalRef(env, ansArray);
  
  mpz_clear(ans);

  //printf ("from C: returning ansArray\n");
  //printf ("from C: sleeping for 3 seconds...\n");
  //fflush(stdout);
  //sleep(3);
  //printf ("from C: done sleeping\n");
  //fflush(stdout);

  return ansArray;
}

