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

#ifndef YAO_H
#define YAO_H

#include <gmp.h>

typedef struct yao_ctx_t {
  mpz_t NSquared;
  int b;
  mpz_t* hs;
  int* valid;
  mpz_t tmp;
  mpz_t a;
} yao_ctx_t;


extern void yao_init(yao_ctx_t *ctx, mpz_t NSquared, int dps);
extern void yao_fini(yao_ctx_t *ctx);
extern void yao_insert_data_part2(yao_ctx_t *ctx, mpz_t queryElement, int part);
extern void yao_compute_column_and_clear_data(yao_ctx_t *ctx, mpz_t out);
extern void yao_clear_data(yao_ctx_t *ctx);

#endif  // YAO_H
