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

#include "org_enquery_encryptedquery_responder_wideskies_common_ComputeEncryptedColumnDeRooijJNI.h"
#include "derooij_wrap.h"
#include "derooij.h"

#include <gmp.h>
#include <assert.h>
#include <stdlib.h>
#include <string.h>

JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_responder_wideskies_common_ComputeEncryptedColumnDeRooijJNI_derooijNew
  (JNIEnv *env, jobject thisObj, jbyteArray jNSquaredBytes, jint jMaxRowIndex)
{
  derooij_wrap_t *wrap;
  derooij_ctx_t *ctx;
  jsize jN2len = (*env)->GetArrayLength(env, jNSquaredBytes);
  jbyte *jN2bytes = (*env)->GetByteArrayElements(env, jNSquaredBytes, NULL);
  mpz_t NSquared;
  int i;

  mpz_init(NSquared);
  mpz_import(NSquared, jN2len, 1, 1, -1, 0, jN2bytes);
  wrap = (derooij_wrap_t *)calloc(1, sizeof(derooij_wrap_t));
  derooij_wrap_init(wrap, NSquared, (int)jMaxRowIndex);
  mpz_clear(NSquared);

  (*env)->ReleaseByteArrayElements(env, jNSquaredBytes, jN2bytes, JNI_ABORT);

  return (jlong)wrap;
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_responder_wideskies_common_ComputeEncryptedColumnDeRooijJNI_derooijSetQueryElement
  (JNIEnv *env, jobject thisObj, jlong hContext, jint jRowIndex, jbyteArray jQueryElementBytes)
{
  jsize jqelen = (*env)->GetArrayLength(env, jQueryElementBytes);
  jbyte *jqebytes = (*env)->GetByteArrayElements(env, jQueryElementBytes, NULL);
  int rowIndex = (int)jRowIndex;

  derooij_wrap_t *wrap = (derooij_wrap_t*)hContext;
  assert (0 <= rowIndex && rowIndex < wrap->max_row_index);
  mpz_import(wrap->query_elements[rowIndex], jqelen, 1, 1, -1, 0, jqebytes);
  (*env)->ReleaseByteArrayElements(env, jQueryElementBytes, jqebytes, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_responder_wideskies_common_ComputeEncryptedColumnDeRooijJNI_derooijInsertDataPart
  (JNIEnv *env, jobject thisObj, jlong hContext, jint jRowIndex, jint jPart)
{
  derooij_wrap_t *wrap = (derooij_wrap_t*)hContext;
  int rowIndex = (int)jRowIndex;
  derooij_wrap_insert_data_part(wrap, rowIndex, (int)jPart);
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_responder_wideskies_common_ComputeEncryptedColumnDeRooijJNI_derooijInsertDataPart2
  (JNIEnv *env, jobject thisObj, jlong hContext, jbyteArray jQueryElementBytes, jint jPart)
{
  jsize jqelen = (*env)->GetArrayLength(env, jQueryElementBytes);
  jbyte *jqebytes = (*env)->GetByteArrayElements(env, jQueryElementBytes, NULL);
  mpz_t queryElement;
  mpz_init(queryElement);

  mpz_import(queryElement, jqelen, 1, 1, -1, 0, jqebytes);

  derooij_wrap_t *wrap = (derooij_wrap_t*)hContext;
  derooij_insert_data_part2(&wrap->derooij_ctx, queryElement, (int)jPart);
  mpz_clear(queryElement);

  (*env)->ReleaseByteArrayElements(env, jQueryElementBytes, jqebytes, JNI_ABORT);
}

JNIEXPORT jbyteArray JNICALL Java_org_enquery_encryptedquery_responder_wideskies_common_ComputeEncryptedColumnDeRooijJNI_derooijComputeColumnAndClearData
  (JNIEnv *env, jobject thisObj, jlong hContext)
{
  mpz_t answer;
  size_t ansbyteslength, tmpsize;
  jbyte *ansbytes;
  jbyteArray ansArray;
  derooij_wrap_t *wrap = (derooij_wrap_t*)hContext;

  mpz_init(answer);
  derooij_compute_column_and_clear_data(&wrap->derooij_ctx, answer);

  ansbyteslength = mpz_sizeinbase(answer, 256);
  ansArray = (*env)->NewByteArray(env, ansbyteslength);

  ansbytes = (*env)->GetByteArrayElements(env, ansArray, NULL);
  mpz_export(ansbytes, &tmpsize, 1, 1, -1, 0, answer);
  assert (tmpsize == ansbyteslength);
  (*env)->ReleaseByteArrayElements(env, ansArray, ansbytes, 0);

  mpz_clear(answer);
  return ansArray;
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_responder_wideskies_common_ComputeEncryptedColumnDeRooijJNI_derooijClearData
  (JNIEnv *env, jobject thisObj, jlong hContext)
{
  derooij_wrap_t *wrap = (derooij_wrap_t*)hContext;
  derooij_clear_data(&wrap->derooij_ctx);
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_responder_wideskies_common_ComputeEncryptedColumnDeRooijJNI_derooijDelete
  (JNIEnv *env, jobject thisObj, jlong hContext)
{
  int i;
  derooij_wrap_t *wrap = (derooij_wrap_t*)hContext;
  derooij_wrap_fini(wrap);
  free(wrap);
}

