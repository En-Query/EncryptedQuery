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

#include "yao_wrap.h"
#include "yao.h"

#include <gmp.h>
#include <assert.h>
#include <stdlib.h>
#include <string.h>

void yao_wrap_init(yao_wrap_t *wrap, mpz_t NSquared, int max_row_index, int b) {
  yao_ctx_t *ctx;
  int i;
  wrap->max_row_index = (int)max_row_index;
  wrap->query_elements = (mpz_t *)calloc(max_row_index, sizeof(mpz_t));
  for (i=0; i<max_row_index; i++) {
    mpz_init(wrap->query_elements[i]);
  }
  ctx = &wrap->yao_ctx;
  yao_init(ctx, NSquared, b);
}

void yao_wrap_fini(yao_wrap_t *wrap) {
  int i;
  for (i = 0; i < wrap->max_row_index; i++) {
    mpz_clear(wrap->query_elements[i]);
  }
  free(wrap->query_elements);
  yao_fini(&wrap->yao_ctx);
}

void yao_wrap_insert_data_part(yao_wrap_t *wrap, int row_index, int part) {
  assert (0 <= row_index && row_index < wrap->max_row_index);
  yao_insert_data_part2(&wrap->yao_ctx, wrap->query_elements[row_index], part);
}
