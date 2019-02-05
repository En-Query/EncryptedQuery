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
