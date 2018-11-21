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

#ifndef YAO_WRAP_H
#define YAO_WRAP_H

#include "yao.h"

typedef struct yao_wrap_t {
  int max_row_index;
  mpz_t *query_elements;
  yao_ctx_t yao_ctx;
} yao_wrap_t;

extern void yao_wrap_init(yao_wrap_t *wrap, mpz_t NSquared, int max_row_index, int b);

extern void yao_wrap_fini(yao_wrap_t *wrap);

extern void yao_wrap_insert_data_part(yao_wrap_t *wrap, int row_index, int part);

#endif // YAO_WRAP_H
