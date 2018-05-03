/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
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
