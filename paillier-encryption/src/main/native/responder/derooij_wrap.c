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

#include "derooij_wrap.h"
#include "derooij.h"

#include <gmp.h>
#include <assert.h>
#include <stdlib.h>
#include <string.h>

void derooij_wrap_init(derooij_wrap_t *wrap, mpz_t NSquared, int max_row_index) {
  derooij_ctx_t *ctx;
  int i;
  wrap->max_row_index = (int)max_row_index;
  wrap->query_elements = (mpz_t *)calloc(max_row_index, sizeof(mpz_t));
  for (i=0; i<max_row_index; i++) {
    mpz_init(wrap->query_elements[i]);
  }
  ctx = &wrap->derooij_ctx;
  derooij_init(ctx, NSquared, max_row_index);
}

void derooij_wrap_fini(derooij_wrap_t *wrap) {
  int i;
  for (i = 0; i < wrap->max_row_index; i++) {
    mpz_clear(wrap->query_elements[i]);
  }
  free(wrap->query_elements);
  derooij_fini(&wrap->derooij_ctx);
}

void derooij_wrap_insert_data_part(derooij_wrap_t *wrap, int row_index, mpz_t part_mpz) {
  assert (0 <= row_index && row_index < wrap->max_row_index);
  derooij_insert_data_part2(&wrap->derooij_ctx, wrap->query_elements[row_index], part_mpz);
}
