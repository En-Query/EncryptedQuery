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

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

#define N 7
int test_maxheap() {
  int input[N] = {10, 2, 1, 3, -4, 9, 17};
  int answer[N] = {17, 10, 9, 3, 2, 1, -4};
  maxheap_t heap;
  int val, i;

  maxheap_init(&heap, N);
  assert (heap.capacity == N);
  assert (heap.size == 0);

  for (i=0; i<N; i++) {
    printf("    inserting %d\n", input[i]);
    maxheap_insert(&heap, input[i]);
    assert (heap.size == i + 1);
  }

  for (i=0; i<N; i++) {
    val = maxheap_extract_max(&heap);
    printf ("    extracted next max: %d\n", val);
    assert (val == answer[i]);
    VALUE_DELETE(val);
    assert (heap.size == N-i-1);
  }

cleanup:
  maxheap_fini(&heap);
	 
  return 0;
}

int main() {
  int status;
  printf ("test_maxheap...\n");
  status = test_maxheap();
  if (status > 0) {
    printf ("failed!\n");
    goto cleanup;
  }
  printf ("Done.\n");

  printf ("All tests passed successfully.\n");

cleanup:
  return status;
}
