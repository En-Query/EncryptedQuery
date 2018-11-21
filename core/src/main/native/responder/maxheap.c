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

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

void maxheap_init(maxheap_t *heap, int capacity) {
  int i;
  memset(heap, 0, sizeof(maxheap_t));
  heap->size = 0;
  heap->capacity = capacity;
  heap->A = (VALUE_T*) calloc(capacity+1, sizeof(VALUE_T));
  for (i = 1; i <= capacity; i++) {
    heap->A[i] = VALUE_NULL();
  }
}

void maxheap_fini(maxheap_t *heap) {
  int i;
  for (i = 1; i <= heap->size; i++) {
    VALUE_DELETE(heap->A[i]);
  }
  free(heap->A);
  memset(heap, 0, sizeof(maxheap_t));
}

void maxheap_clear(maxheap_t *heap) {
  int i;
  for (i = 1; i <= heap->size; i++) {
    VALUE_DELETE(heap->A[i]);
    heap->A[i] = VALUE_NULL();
  }
  heap->size = 0;
}

void maxheap_heapify(maxheap_t *heap, int i) {
  VALUE_T *A;
  int l;
  int r;
  int largest;
  VALUE_T tmp;
  A = heap->A;
 beginning:
  l = LEFT(i);
  r = RIGHT(i);
  if (l <= heap->size && VALUE_CMP(A[l],A[i]) > 0) {
    largest = l;
  } else {
    largest = i;
  }
  if (r <= heap->size && VALUE_CMP(A[r],A[largest]) > 0) {
    largest = r;
  }
  if (largest != i) {
    tmp = A[largest];
    A[largest] = A[i];
    A[i] = tmp;
    i = largest;
    goto beginning;
  }
  return;
}

void maxheap_insert(maxheap_t *heap, VALUE_T val) {
  int i;
  VALUE_T tmp;
  VALUE_T *A = heap->A;
  assert (heap->size < heap->capacity);
  heap->size++;
  i = heap->size;
  A[i] = val;
  // propagate up
  while (i > 1 && VALUE_CMP(A[PARENT(i)],A[i]) < 0) {
    tmp = A[i];
    A[i] = A[PARENT(i)];
    A[PARENT(i)] = tmp;
    i = PARENT(i);
  }
}

VALUE_T maxheap_max(maxheap_t *heap) {
  assert (heap->size > 0);
  return heap->A[1];
}

VALUE_T maxheap_extract_max(maxheap_t *heap) {
  VALUE_T ans;
  VALUE_T *A;

  assert (heap->size > 0);
  A = heap->A;

  ans = A[1];
  A[1] = A[heap->size];
  heap->size--;
  maxheap_heapify(heap, 1);

  return ans;
}


