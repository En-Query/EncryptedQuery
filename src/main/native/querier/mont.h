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

#ifndef MONT_H
#define MONT_H

#include <gmp.h>

typedef struct mont_t {
  mpz_t N;
  int Rlen;
  mpz_t Nprime;
} mont_t;

extern void mont_init(mont_t *mont, mpz_t N);
extern void mont_clear(mont_t *mont);
extern void mont_to_mont(mont_t *mont, mpz_t xm, mpz_t x);
extern void REDC(mont_t *mont, mpz_t rop, mpz_t x, mpz_t tmp1, mpz_t tmp2);
extern void mont_from_mont(mont_t *mont, mpz_t x, mpz_t xm, mpz_t tmp1, mpz_t tmp2);
extern void mont_multiply(mont_t *mont, mpz_t xym, mpz_t xm, mpz_t ym, mpz_t tmp1, mpz_t tmp2, mpz_t tmp3);

#endif
