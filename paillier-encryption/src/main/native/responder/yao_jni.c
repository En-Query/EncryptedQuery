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

#include "org_enquery_encryptedquery_encryption_paillier_YaoJNIColumnProcessor.h"
#include "yao_wrap.h"
#include "yao.h"

#include <gmp.h>
#include <assert.h>
#include <stdlib.h>
#include <string.h>

JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_encryption_paillier_YaoJNIColumnProcessor_yaoNew
  (JNIEnv *env, jobject thisObj, jbyteArray jNSquaredBytes, jint jMaxRowIndex, jint b)
{
  yao_wrap_t *wrap;
  yao_ctx_t *ctx;
  jsize jN2len = (*env)->GetArrayLength(env, jNSquaredBytes);
  jbyte *jN2bytes = (*env)->GetByteArrayElements(env, jNSquaredBytes, NULL);
  mpz_t NSquared;
  int i;

  mpz_init(NSquared);
  mpz_import(NSquared, jN2len, 1, 1, -1, 0, jN2bytes);
  wrap = (yao_wrap_t *)calloc(1, sizeof(yao_wrap_t));
  yao_wrap_init(wrap, NSquared, (int)jMaxRowIndex, (int)b);
  mpz_clear(NSquared);

  (*env)->ReleaseByteArrayElements(env, jNSquaredBytes, jN2bytes, JNI_ABORT);

  return (jlong)wrap;
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_paillier_YaoJNIColumnProcessor_yaoSetQueryElement
  (JNIEnv *env, jobject thisObj, jlong hContext, jint jRowIndex, jbyteArray jQueryElementBytes)
{
  jsize jqelen = (*env)->GetArrayLength(env, jQueryElementBytes);
  jbyte *jqebytes = (*env)->GetByteArrayElements(env, jQueryElementBytes, NULL);
  int rowIndex = (int)jRowIndex;

  yao_wrap_t *wrap = (yao_wrap_t*)hContext;
  assert (0 <= rowIndex && rowIndex < wrap->max_row_index);
  mpz_import(wrap->query_elements[rowIndex], jqelen, 1, 1, -1, 0, jqebytes);
  (*env)->ReleaseByteArrayElements(env, jQueryElementBytes, jqebytes, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_paillier_YaoJNIColumnProcessor_yaoInsertDataPart
  (JNIEnv *env, jobject thisObj, jlong hContext, jint jRowIndex, jint jPart)
{
  yao_wrap_t *wrap = (yao_wrap_t*)hContext;
  int rowIndex = (int)jRowIndex;
  yao_wrap_insert_data_part(wrap, rowIndex, (int)jPart);
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_paillier_YaoJNIColumnProcessor_yaoInsertDataPart2
  (JNIEnv *env, jobject thisObj, jlong hContext, jbyteArray jQueryElementBytes, jint jPart)
{
  jsize jqelen = (*env)->GetArrayLength(env, jQueryElementBytes);
  jbyte *jqebytes = (*env)->GetByteArrayElements(env, jQueryElementBytes, NULL);
  mpz_t queryElement;
  mpz_init(queryElement);

  mpz_import(queryElement, jqelen, 1, 1, -1, 0, jqebytes);

  yao_wrap_t *wrap = (yao_wrap_t*)hContext;
  yao_insert_data_part2(&wrap->yao_ctx, queryElement, (int)jPart);
  mpz_clear(queryElement);

  (*env)->ReleaseByteArrayElements(env, jQueryElementBytes, jqebytes, JNI_ABORT);
}

JNIEXPORT jbyteArray JNICALL Java_org_enquery_encryptedquery_encryption_paillier_YaoJNIColumnProcessor_yaoComputeColumnAndClearData
  (JNIEnv *env, jobject thisObj, jlong hContext)
{
  mpz_t answer;
  size_t ansbyteslength, tmpsize;
  jbyte *ansbytes;
  jbyteArray ansArray;
  yao_wrap_t *wrap = (yao_wrap_t*)hContext;

  mpz_init(answer);
  yao_compute_column_and_clear_data(&wrap->yao_ctx, answer);

  ansbyteslength = mpz_sizeinbase(answer, 256);
  ansArray = (*env)->NewByteArray(env, ansbyteslength);

  ansbytes = (*env)->GetByteArrayElements(env, ansArray, NULL);
  mpz_export(ansbytes, &tmpsize, 1, 1, -1, 0, answer);
  assert (tmpsize == ansbyteslength);
  (*env)->ReleaseByteArrayElements(env, ansArray, ansbytes, 0);

  mpz_clear(answer);
  return ansArray;
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_paillier_YaoJNIColumnProcessor_yaoClearData
  (JNIEnv *env, jobject thisObj, jlong hContext)
{
  yao_wrap_t *wrap = (yao_wrap_t*)hContext;
  yao_clear_data(&wrap->yao_ctx);
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_paillier_YaoJNIColumnProcessor_yaoDelete
  (JNIEnv *env, jobject thisObj, jlong hContext)
{
  int i;
  yao_wrap_t *wrap = (yao_wrap_t*)hContext;
  yao_wrap_fini(wrap);
  free(wrap);
}

