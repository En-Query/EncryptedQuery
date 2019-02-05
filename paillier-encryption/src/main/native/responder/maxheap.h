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

#ifndef MAXHEAP_H
#define MAXHEAP_H

#define PARENT(i)        ((i)/2)
#define LEFT(i)          (2*(i))
#define RIGHT(i)         (2*(i)+1)

typedef struct maxheap_t {
  int size;
  int capacity;
  VALUE_T *A;
} maxheap_t;

static inline int maxheap_size(maxheap_t *heap) {
  return heap->size;
}

extern void maxheap_init(maxheap_t *heap, int capacity);
extern void maxheap_fini(maxheap_t *heap);
extern void maxheap_clear(maxheap_t *heap);
extern void maxheap_heapify(maxheap_t *heap, int i);
extern void maxheap_insert(maxheap_t *heap, VALUE_T val);
extern VALUE_T maxheap_get_max(maxheap_t *heap);
extern VALUE_T maxheap_extract_max(maxheap_t *heap);

#endif // MAXHEAP_H
