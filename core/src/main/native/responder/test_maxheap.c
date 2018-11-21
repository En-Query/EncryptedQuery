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
