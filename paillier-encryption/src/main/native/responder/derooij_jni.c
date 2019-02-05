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

#include "org_enquery_encryptedquery_encryption_paillier_DeRooijJNIColumnProcessor.h"
#include "derooij_wrap.h"
#include "derooij.h"

#include <gmp.h>
#include <assert.h>
#include <stdlib.h>
#include <string.h>

JNIEXPORT jlong JNICALL Java_org_enquery_encryptedquery_encryption_paillier_DeRooijJNIColumnProcessor_derooijNew
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

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_paillier_DeRooijJNIColumnProcessor_derooijSetQueryElement
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

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_paillier_DeRooijJNIColumnProcessor_derooijInsertDataPartBytes
  (JNIEnv *env, jobject thisObj, jlong hContext, jint jRowIndex, jbyteArray jPartBytes, jint jInputOffset, jint jInputLen)
{
  jbyte *jpartbytes = (*env)->GetByteArrayElements(env, jPartBytes, NULL);
  mpz_t part_mpz;
  mpz_init(part_mpz);
  mpz_import(part_mpz, (size_t)jInputLen, 1, 1, -1, 0, jpartbytes + (size_t)jInputOffset);
  (*env)->ReleaseByteArrayElements(env, jPartBytes, jpartbytes, JNI_ABORT);
  derooij_wrap_t *wrap = (derooij_wrap_t*)hContext;
  int rowIndex = (int)jRowIndex;
  derooij_wrap_insert_data_part(wrap, rowIndex, part_mpz);
  mpz_clear(part_mpz);
}

JNIEXPORT jbyteArray JNICALL Java_org_enquery_encryptedquery_encryption_paillier_DeRooijJNIColumnProcessor_derooijComputeColumnAndClearData
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

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_paillier_DeRooijJNIColumnProcessor_derooijClearData
  (JNIEnv *env, jobject thisObj, jlong hContext)
{
  derooij_wrap_t *wrap = (derooij_wrap_t*)hContext;
  derooij_clear_data(&wrap->derooij_ctx);
}

JNIEXPORT void JNICALL Java_org_enquery_encryptedquery_encryption_paillier_DeRooijJNIColumnProcessor_derooijDelete
  (JNIEnv *env, jobject thisObj, jlong hContext)
{
  int i;
  derooij_wrap_t *wrap = (derooij_wrap_t*)hContext;
  derooij_wrap_fini(wrap);
  free(wrap);
}

