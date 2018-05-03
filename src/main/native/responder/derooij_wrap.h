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

#ifndef DEROOIJ_WRAP_H
#define DEROOIJ_WRAP_H

#include "derooij.h"

typedef struct derooij_wrap_t {
  int max_row_index;
  mpz_t *query_elements;
  derooij_ctx_t derooij_ctx;
} derooij_wrap_t;

extern void derooij_wrap_init(derooij_wrap_t *wrap, mpz_t NSquared, int max_row_index);

extern void derooij_wrap_fini(derooij_wrap_t *wrap);

extern void derooij_wrap_insert_data_part(derooij_wrap_t *wrap, int row_index, int part);

#endif // YAO_WRAP_H
