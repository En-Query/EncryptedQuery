/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */

#ifndef CUDA_RESP_H
#define CUDA_RESP_H

#define USE_RADIXN

#include "maxheap_defs_derooij.h"
#include "derooij.h"

#include <cstdint>
#include <cstring>
#include <list>
#include <unordered_map>
#include <vector>

#include <gmp.h>

#include "xmp.h"
#include <cuda_runtime_api.h>

#define XMP_CHECK_ERROR(s,fun)						\
    {									\
	xmpError_t error = fun;						\
	if(error!=xmpErrorSuccess) {					\
	    if(error==xmpErrorCuda) {					\
		cudaError_t e = cudaPeekAtLastError();			\
		printf("CUDA Error %d (%s), %s:%d\n",(int)e,cudaGetErrorString(e),__FILE__,__LINE__); \
	    } else							\
		printf("XMP Error %s, %s:%d\n",xmpGetErrorString(error),__FILE__,__LINE__); \
	    s=false;							\
	} else {							\
	    s=true;							\
	}								\
    }

typedef uint32_t binnum_t;

typedef struct bin_t {
    std::vector<const uint32_t*> p;
    std::vector<size_t> r;
    binnum_t binnum;
    bin_t(binnum_t binnum);
    bin_t(binnum_t binnum, const uint32_t *val_ptr);
    bin_t(binnum_t binnum, const uint32_t *val_ptr, size_t row);
} bin_t;

typedef struct bins_t {
    std::unordered_map<binnum_t,size_t> m;
    std::vector<bin_t> l;
    binnum_t max_binnum;
    std::vector<uint32_t> binprods;
    void insert_val_ptr(binnum_t binnum, const uint32_t *val_ptr);
    void insert_val_ptr(binnum_t binnum, const uint32_t *val_ptr, size_t row);
    void clear();
} bins_t;

typedef struct query_t {
    std::string uuid;
    int ref_count;
    size_t w;  // number of 32-bit limbs in N
    size_t w2; // 2*w
    mpz_t Nz;
    mpz_t N2z;
    size_t num_qelts;
    std::vector<uint32_t> qelts;
    derooij_ctx_t derooij_ctx;
    mpz_t *query_elements;
    query_t(const std::string &uuid, size_t w, mpz_t N, size_t num_qelts);
    ~query_t();
    void set_qelt(size_t row, mpz_t qelt);
    void words_from_mpzt(uint32_t *words, mpz_t z);
    void mpzt_from_words(mpz_t z, const uint32_t *words);
    void words_from_mpzt(uint32_t *words_lo, uint32_t *words_hi, mpz_t z);
    void mpzt_from_words(mpz_t z, const uint32_t *words_lo, const uint32_t *words_hi);
} query_t;

typedef struct device_t {
    int device;
    bool busy;
    size_t batch_size;
    size_t w;
    size_t w2;
    xmpHandle_t handle;
#ifdef USE_RADIXN
    xmpIntegers_t N;
    xmpIntegers_t x_lo, x_hi;  // A, B
    xmpIntegers_t y_lo, y_hi;  // C, D
    xmpIntegers_t out_lo, out_hi; // outlos, outhis
    // temps
    xmpIntegers_t AChi;
    xmpIntegers_t tmp1;
    xmpIntegers_t tmp2;
    xmpIntegers_t tmp3;
    xmpIntegers_t tmp4;
#else
    xmpIntegers_t modulus;
    xmpIntegers_t x;
    xmpIntegers_t y;
    xmpIntegers_t xy;
    xmpIntegers_t out;
#endif
    device_t(int device, size_t w, size_t batch_size);
    ~device_t();
    void set_modulus(query_t &query);
#ifdef USE_RADIXN
    void batch_modmul_radixn(query_t &query, uint32_t *oo_lo, uint32_t *oo_hi, uint32_t *xx_lo, uint32_t *xx_hi, uint32_t *yy_lo, uint32_t *yy_hi, size_t num);
#else
    void batch_modmul(query_t &query, uint32_t *oo, uint32_t *xx, uint32_t *yy, size_t num);
#endif
} device_t;

typedef struct yao_t {
    query_t &query;
    size_t batch_size;
    bins_t bins;
    yao_t(query_t &query, size_t batch_size);
    void insert_chunk(size_t row, binnum_t chunk);
    void insert_chunk_bi(uint32_t *v, binnum_t chunk);
    void compute(device_t &dev, mpz_t result, int depth);
    void clear();
    void dump();
} yao_t;

void compute_binprods(device_t &dev, query_t &query, size_t batch_size, bins_t &bins, int depth);
void combine_binprods(device_t &dev, query_t &query, size_t batch_size, mpz_t result, bins_t &bins, int depth);

#endif
