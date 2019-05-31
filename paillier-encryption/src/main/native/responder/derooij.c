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

#include "maxheap.h"
#include "derooij.h"

#include <gmp.h>
#include <string.h>

#ifdef __cplusplus
extern "C" {
#endif

static void derooij_node_init(derooij_node_t *node, mpz_t key, mpz_t value) {
  mpz_init_set(node->key, key);
  mpz_init_set(node->value, value);
}

static void derooij_node_fini(derooij_node_t *node) {
  mpz_clear(node->key);
  mpz_clear(node->value);
}

static derooij_node_t* derooij_node_new(mpz_t key, mpz_t value) {
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

void derooij_insert_data_part2(derooij_ctx_t *ctx, mpz_t queryElement, mpz_t part) {
  if (mpz_sgn(part) != 0) {
    derooij_node_t *node = derooij_node_new(part, queryElement);
    maxheap_insert(&ctx->heap, node);
  }
}

void derooij_compute_column_and_clear_data(derooij_ctx_t *ctx, mpz_t out) {
  maxheap_t *heap = &ctx->heap;
  derooij_node_t *node, *node1, *node2;
  mpz_t power, q, r;
  if (maxheap_size(heap) <= 0) {
    mpz_set_si(out, 1L);
    return;
  }
  mpz_inits(power, q, r, NULL);
  while (maxheap_size(heap) > 1) {
    node1 = maxheap_extract_max(heap);
    node2 = maxheap_extract_max(heap);
    //    a = node1->key;
    //    b = node2->key;
    //    q = a / b;
    //    r = a % b;
    mpz_fdiv_qr(q, r, node1->key, node2->key);
    mpz_powm(power, node1->value, q, ctx->NSquared);
    mpz_mul(node2->value, node2->value, power);
    mpz_fdiv_r(node2->value, node2->value, ctx->NSquared);
    maxheap_insert(heap, node2);
    if (mpz_sgn(r) != 0) {
      mpz_set(node1->key, r);
      maxheap_insert(heap, node1);
    } else {
      derooij_node_delete(node1);
    }
  }

  // at this point heap size must be 1
  node = maxheap_extract_max(heap);
  mpz_powm(out, node->value, node->key, ctx->NSquared);
  derooij_node_delete(node);
  mpz_clears(power, q, r, NULL);
}

void derooij_clear_data(derooij_ctx_t *ctx) {
  maxheap_clear(&ctx->heap);
}

#ifdef __cplusplus
}
#endif
