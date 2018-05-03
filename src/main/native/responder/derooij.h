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
