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
