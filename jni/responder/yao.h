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
