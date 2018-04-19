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

#include "maxheap.h"
#include "derooij.h"

#include <gmp.h>
#include <string.h>

static void derooij_node_init(derooij_node_t *node, int key, mpz_t value) {
  node->key = key;
  mpz_init_set(node->value, value);
}

static void derooij_node_fini(derooij_node_t *node) {
  mpz_clear(node->value);
}

static derooij_node_t* derooij_node_new(int key, mpz_t value) {
  derooij_node_t *node = (derooij_node_t*) calloc(1, sizeof(*node));
  derooij_node_init(node, key, value);
  return node;
}

static void derooij_node_delete(derooij_node_t *node) {
  derooij_node_fini(node);
  free(node);
}

void derooij_init(derooij_ctx_t *ctx, mpz_t NSquared, int capacity) {
  memset(ctx, 0, sizeof(derooij_ctx_t));
  mpz_init_set(ctx->NSquared, NSquared);
  maxheap_init(&ctx->heap, capacity);
}

void derooij_fini(derooij_ctx_t *ctx) {
  mpz_clear(ctx->NSquared);
  maxheap_fini(&ctx->heap);
  memset(ctx, 0, sizeof(derooij_ctx_t));
}

void derooij_insert_data_part2(derooij_ctx_t *ctx, mpz_t queryElement, int part) {
  if (part != 0) {
    derooij_node_t *node = derooij_node_new(part, queryElement);
    maxheap_insert(&ctx->heap, node);
  }
}

void derooij_compute_column_and_clear_data(derooij_ctx_t *ctx, mpz_t out) {
  maxheap_t *heap = &ctx->heap;
  derooij_node_t *node, *node1, *node2;
  mpz_t power;
  int a, b, q, r;
  if (maxheap_size(heap) <= 0) {
    mpz_set_si(out, 1L);
    return;
  }
  mpz_init(power);
  while (maxheap_size(heap) > 1) {
    // TODO: continue here
    node1 = maxheap_extract_max(heap);
    a = node1->key;
    node2 = maxheap_extract_max(heap);
    b = node2->key;
    q = a / b;
    r = a % b;
    mpz_powm_ui(power, node1->value, q, ctx->NSquared);
    mpz_mul(node2->value, node2->value, power);
    mpz_fdiv_r(node2->value, node2->value, ctx->NSquared);
    maxheap_insert(heap, node2);
    if (r != 0) {
      node1->key = r;
      maxheap_insert(heap, node1);
    } else {
      derooij_node_delete(node1);
    }
  }

  // at this point heap size must be 1
  node = maxheap_extract_max(heap);
  mpz_powm_ui(out, node->value, (unsigned long)node->key, ctx->NSquared);
  derooij_node_delete(node);
  mpz_clear(power);
}

void derooij_clear_data(derooij_ctx_t *ctx) {
  maxheap_clear(&ctx->heap);
}
