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

#ifndef DEROOIJ_H
#define DEROOIJ_H

#include "maxheap.h"

#include <gmp.h>

typedef struct derooij_ctx_t {
  mpz_t NSquared;
  maxheap_t heap;
} derooij_ctx_t;


extern void derooij_init(derooij_ctx_t *ctx, mpz_t NSquared, int capacity);
extern void derooij_fini(derooij_ctx_t *ctx);
extern void derooij_insert_data_part2(derooij_ctx_t *ctx, mpz_t queryElement, int part);
extern void derooij_compute_column_and_clear_data(derooij_ctx_t *ctx, mpz_t out);
extern void derooij_clear_data(derooij_ctx_t *ctx);

#endif  // DEROOIJ_H
