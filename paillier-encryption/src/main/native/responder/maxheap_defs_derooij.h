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

#ifndef MAXHEAP_DEFS_DEROOIJ_H
#define MAXHEAP_DEFS_DEROOIJ_H

#include <gmp.h>
#include <stdlib.h>

typedef struct derooij_node_t {
  mpz_t key;
  mpz_t value;
} derooij_node_t;

/* the following definitions are needed to instantiate maxheap */
typedef derooij_node_t    (*VALUE_T);
#define VALUE_NULL()  	   NULL
#define VALUE_DELETE(x)   do { mpz_clear((x)->value); free(x); } while(0)
#define VALUE_CMP(x,y) 	 (mpz_cmp((x)->key,(y)->key))


#endif // MAXHEAP_DEFS_DEROOIJ_H
