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
