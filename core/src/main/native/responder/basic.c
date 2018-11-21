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

#include "basic.h"

#include <gmp.h>
#include <stdlib.h>
#include <string.h>

void basic_init(basic_ctx_t *ctx, mpz_t NSquared) {
  int bitcnt = mpz_sizeinbase(NSquared, 2);
  mpz_init_set(ctx->NSquared, NSquared);
  mpz_init2(ctx->prod, bitcnt);
  mpz_set_si(ctx->prod, 1L);
  mpz_init2(ctx->tmp, bitcnt);
  mpz_init2(ctx->tmp2, 2*bitcnt);
}

void basic_fini(basic_ctx_t *ctx) {
  mpz_clears(ctx->NSquared, ctx->prod, ctx->tmp, ctx->tmp2, NULL);
  memset(ctx, 0, sizeof(basic_ctx_t));
}

void basic_insert_data_part2(basic_ctx_t *ctx, mpz_t queryElement, int part) {
  mpz_powm_ui(ctx->tmp, queryElement, (unsigned long)part, ctx->NSquared);
  mpz_mul(ctx->tmp2, ctx->prod, ctx->tmp);
  mpz_fdiv_r(ctx->prod, ctx->tmp2, ctx->NSquared);
}

void basic_compute_column_and_clear_data(basic_ctx_t *ctx, mpz_t out) {
  mpz_set(out, ctx->prod);
  mpz_set_si(ctx->prod, 1L);
}
