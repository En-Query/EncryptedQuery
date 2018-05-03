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

#ifndef MAXHEAP_DEFS_DEROOIJ_H
#define MAXHEAP_DEFS_DEROOIJ_H

#include <gmp.h>
#include <stdlib.h>

typedef struct derooij_node_t {
  int key;
  mpz_t value;
} derooij_node_t;

/* the following definitions are needed to instantiate maxheap */
typedef derooij_node_t    (*VALUE_T);
#define VALUE_NULL()  	   NULL
#define VALUE_DELETE(x)   do { mpz_clear((x)->value); free(x); } while(0)
#define VALUE_CMP(x,y) 	 ((x)->key - (y)->key)


#endif // MAXHEAP_DEFS_DEROOIJ_H
