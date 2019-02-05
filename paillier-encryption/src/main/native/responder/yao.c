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

#include "yao.h"

#include <gmp.h>

#include <assert.h>
#include <stdlib.h>
#include <string.h>

void yao_init(yao_ctx_t *ctx, mpz_t NSquared, int dps)
{
  int i;
  mpz_init_set(ctx->NSquared, NSquared);
  ctx->b = dps;
  ctx->hs = (mpz_t*)calloc(1<<dps, sizeof(mpz_t));
  size_t bitsize = mpz_sizeinbase(NSquared, 2);
  for (i = 0; i < (1<<dps); i++) {
    mpz_init2(ctx->hs[i], bitsize);
  }
  ctx->valid = (int*)calloc(1<<ctx->b, sizeof(int));
  // calloc() already sets the array to zero

  mpz_init2(ctx->tmp, 2*bitsize);
  mpz_init2(ctx->a, 2*bitsize);
}

void yao_fini(yao_ctx_t *ctx)
{
  int i;
  for (i = 0; i < (1<<ctx->b); i++) {
    mpz_clear(ctx->hs[i]);
  }
  free(ctx->hs);
  free(ctx->valid);
  mpz_clears(ctx->NSquared, ctx->tmp, ctx->a, NULL);
  memset(ctx, 0, sizeof(yao_ctx_t));
}
			    
void yao_insert_data_part2(yao_ctx_t *ctx, mpz_t queryElement, int part) {
  if (0 == part) {
    return;
  }
  assert (part < (1<<ctx->b));
	  
  if (ctx->valid[part]) {
    mpz_mul(ctx->tmp, ctx->hs[part], queryElement);
    mpz_fdiv_r(ctx->hs[part], ctx->tmp, ctx->NSquared);
  } else {
    mpz_set(ctx->hs[part], queryElement);
    ctx->valid[part] = 1;
  }
}
			    
void yao_compute_column_and_clear_data(yao_ctx_t *ctx, mpz_t out) {
  int x;
  mpz_set_si(out, 1L);
  mpz_set_si(ctx->a, 1L);
  for (x = (1<<ctx->b)-1; x > 0; x--) {
    if (ctx->valid[x]) {
      mpz_mul(ctx->a, ctx->a, ctx->hs[x]);
      mpz_fdiv_r(ctx->a, ctx->a, ctx->NSquared);
    }
    mpz_mul(ctx->tmp, out, ctx->a);
    mpz_fdiv_r(out, ctx->tmp, ctx->NSquared);
  }
  // invalidate
  for (x = 0; x < (1<<ctx->b); x++) {
    ctx->valid[x] = 0;
  }
}

void yao_clear_data(yao_ctx_t *ctx) {
  int x;
  for (x = 0; x < (1<<ctx->b); x++) {
    ctx->valid[x] = 0;
  }
}
