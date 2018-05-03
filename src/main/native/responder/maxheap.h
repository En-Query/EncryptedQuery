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
