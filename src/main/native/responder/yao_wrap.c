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
