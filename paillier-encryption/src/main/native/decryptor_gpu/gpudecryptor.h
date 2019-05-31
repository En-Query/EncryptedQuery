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

#ifndef GPU_DECRYPTOR_H
#define GPU_DECRYPTOR_H

#include "resources.h"

#include "xmp.h"
#include <mutex>
#include <vector>
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <cuda_runtime_api.h>

#include <gmp.h>

#include <inttypes.h>

// hard-coded sizes for XMP integers
const size_t PT_BYTES = 384;
const size_t CT_BYTES = 2*PT_BYTES;
const size_t P1_BYTES = 32;

extern std::mutex cm;
#define COUT_BEGIN { std::lock_guard<std::mutex> cguard(cm);
#define COUT_END }

typedef int64_t handle_t;
const handle_t HANDLE_ERROR = 0;

const policy_t DEFAULT_BUSY_POLICY = RP_POLICY_CALLER_RUNS;

////////////////////////////////////////////////////////////

typedef struct device_t {
    int gpu_id;
    bool busy;

    // how much to send to GPU at a time
    int batch_size;

    // temporary buffers for moving data to and from XMP
    std::vector<uint8_t> xmpbuf_p;
    std::vector<uint8_t> xmpbuf_q;

    // GPU device handle
    xmpHandle_t handle;

    xmpIntegers_t xiOut;
    xmpIntegers_t xiBase;
    xmpIntegers_t xiP1;
    xmpIntegers_t xiQ1;
    xmpIntegers_t xiP2;
    xmpIntegers_t xiQ2;

    device_t(int gpu_id, int batch_size);
    ~device_t();
} device_t;

////////////////////////////////////////////////////////////

typedef struct gpudecryptor_ctx_t {
    // private key
    mpz_t p, q;
    mpz_t p1, q1;
    mpz_t p2, q2;
    mpz_t N;
    mpz_t wp, wq;
    mpz_t crt;
    int plaintext_byte_size;
    int ciphertext_byte_size;
    gpudecryptor_ctx_t(mpz_t p, mpz_t q, mpz_t p1, mpz_t p2);
    ~gpudecryptor_ctx_t();
} gpudecryptor_ctx_t;

////////////////////////////////////////////////////////////

bool initialize_native_library(void);
bool close_native_library(void);
bool set_busy_policy(policy_t policy);

extern handle_t gpudecryptor_new(mpz_t p, mpz_t q, mpz_t p1, mpz_t q1);
extern void gpudecryptor_delete(handle_t h_context);
extern void gpudecryptor_decrypt_cpu(handle_t h_context, mpz_t out, mpz_t ciphertext);
extern int gpudecryptor_decrypt_batch(handle_t h_context, uint8_t* buf, int num_ciphs, bool use_gpu=true);

extern void mpz_from_be(mpz_t rop, const uint8_t bytes[], int num_bytes);

#endif //  GPU_DECRYPTOR_H
