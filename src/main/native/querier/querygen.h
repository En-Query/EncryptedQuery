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

#ifndef QUERYGEN_H
#define QUERYGEN_H

#include "mont.h"
#include <gmp.h>

typedef struct encquery_ctx_t {
  mpz_t N;
  mpz_t N2;
  mont_t mont_p2;
  mont_t mont_q2;
  mpz_t one_mont_p2;
  mpz_t one_mont_q2;
  mpz_t e_mont_p2;   // 1 mod p^2, 0 mod q^2
  mpz_t e_mont_q2;   // 0 mod p^2, 1 mod q^2
  mpz_t gen_p2;
  mpz_t gen_q2;
  int window_size;
  int num_windows;
  int wins_per_byte;
  unsigned char winmask;
  mpz_t **lut_p2;
  mpz_t **lut_q2;
} encquery_ctx_t;

extern encquery_ctx_t* encquery_ctx_new(mpz_t p, mpz_t gen_p2, mpz_t q, mpz_t gen_q2, int window_size, int num_windows);

extern void encquery_encrypt(encquery_ctx_t *ctx, mpz_t ans, int bit_index, const unsigned char *rpbytes, int rpbyteslen, const unsigned char *rqbytes, int rqbyteslen);

#endif
