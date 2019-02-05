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

#ifndef DEROOIJ_WRAP_H
#define DEROOIJ_WRAP_H

#include "derooij.h"

#include <gmp.h>

typedef struct derooij_wrap_t {
  int max_row_index;
  mpz_t *query_elements;
  derooij_ctx_t derooij_ctx;
} derooij_wrap_t;

extern void derooij_wrap_init(derooij_wrap_t *wrap, mpz_t NSquared, int max_row_index);

extern void derooij_wrap_fini(derooij_wrap_t *wrap);

extern void derooij_wrap_insert_data_part(derooij_wrap_t *wrap, int row_index, mpz_t part_mpz);

#endif // YAO_WRAP_H
