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

#include "mont.h"
#include <gmp.h>
#include <string.h>

void mont_init(mont_t *mont, mpz_t N) {
  mpz_t R, negN;

  mpz_init(R);
  mpz_init(negN);

  mpz_init_set(mont->N, N);
  mont->Rlen = (int) mpz_sizeinbase(N, 2);
  mpz_setbit(R, mont->Rlen);
  mpz_neg(negN, N);
  mpz_init(mont->Nprime);
  mpz_invert(mont->Nprime, negN, R);
  //printf ("mont->Rlen: %d\n", mont->Rlen);

  mpz_clear(R);
  mpz_clear(negN);
}
  

void mont_clear(mont_t *mont) {
  mpz_clear(mont->N);
  mpz_clear(mont->Nprime);
  memset(mont, 0, sizeof(mont_t));
}

void mont_to_mont(mont_t *mont, mpz_t xm, mpz_t x) {
  mpz_mul_2exp(xm, x, mont->Rlen);
  mpz_mod(xm, xm, mont->N);
}

void REDC(mont_t *mont, mpz_t rop, mpz_t x, mpz_t tmp1, mpz_t tmp2) {
  // m = (x * Nprime) mod R
  //gmp_printf("REDC: xmym = %Zd\n", x);
  mpz_fdiv_r_2exp(tmp2, x, mont->Rlen);
  mpz_mul(tmp1, tmp2, mont->Nprime);  // TODO: how to avoid computing the high bits?
  mpz_fdiv_r_2exp(tmp1, tmp1, mont->Rlen);
  //gmp_printf("m = %Zd\n", tmp1);
  // t = (x + m * N) / R
  mpz_mul(tmp2, tmp1, mont->N);
  mpz_add(tmp2, tmp2, x);
  mpz_fdiv_q_2exp(rop, tmp2, mont->Rlen);
  if (mpz_cmp(rop, mont->N) >= 0) {
    mpz_sub(rop, rop, mont->N);
  }
}

void mont_from_mont(mont_t *mont, mpz_t x, mpz_t xm, mpz_t tmp1, mpz_t tmp2) {
  REDC(mont, x, xm, tmp1, tmp2);
}

void mont_multiply(mont_t *mont, mpz_t xym, mpz_t xm, mpz_t ym, mpz_t tmp1, mpz_t tmp2, mpz_t tmp3) {
  //printf ("\ncalling mont_multiply\n\n");
  //mpz_mul(xym, xm, ym);
  mpz_mul(tmp3, xm, ym);
  REDC(mont, xym, tmp3, tmp1, tmp2);
  //printf ("\nexiting mont_multiply\n\n");
}
