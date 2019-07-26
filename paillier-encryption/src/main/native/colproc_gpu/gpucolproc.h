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

#ifndef LIBGPURESPONDER_H
#define LIBGPURESPONDER_H

#include "maxheap_defs_derooij.h"
#include "derooij.h"

#include "cuda_resp.h"
#include "resources.h"

#include <cstdint>
#include <fstream>
#include <iostream>
#include <memory>
#include <string>

//#define GPURESPONDER_DEBUG

const std::size_t BATCH_SIZE = 8192;
const std::size_t DEVICE_W = 96; 

extern bool logging;
extern std::string log_file_prefix;

extern std::mutex cm;
#define COUT_BEGIN { std::lock_guard<std::mutex> cguard(cm);
#define COUT_END }

typedef int64_t handle_t;
const handle_t HANDLE_ERROR = 0;

const policy_t DEFAULT_BUSY_POLICY = RP_POLICY_CALLER_RUNS;

typedef struct colproc_t {
    yao_t yao;
    query_t *query;
    derooij_ctx_t derooij_ctx;
    std::shared_ptr<device_t> device;
    bool use_logging;
    std::string log_file_path;
    std::ofstream logfile;
    colproc_t(query_t *query, size_t batch_size);
    ~colproc_t();
    bool insert_chunk(size_t row, uint32_t chunk);
    bool compute_and_clear(device_t &device, mpz_t result);
    bool clear();
} colproc_t;

extern "C" {

typedef bool initialize_native_library_t(void);
initialize_native_library_t initialize_native_library;

typedef bool close_native_library_t(void);
close_native_library_t close_native_library;

typedef bool set_busy_policy_t(policy_t policy);
set_busy_policy_t set_busy_policy;

typedef handle_t create_query_t(const std::string &uuid, mpz_t N, size_t num_qelts);
create_query_t create_query;

typedef bool query_set_qelt_t(handle_t h_query, size_t row, mpz_t qelt);
query_set_qelt_t query_set_qelt;

typedef bool remove_query_t(handle_t h_query);
remove_query_t remove_query;

typedef handle_t create_colproc_t(handle_t h_query);
create_colproc_t create_colproc;

typedef bool remove_colproc_t(handle_t h_colproc);
remove_colproc_t remove_colproc;

typedef bool colproc_insert_chunk_t(handle_t h_colproc, size_t row, uint32_t chunk);
colproc_insert_chunk_t colproc_insert_chunk;

typedef int colproc_compute_and_clear_t(handle_t h_colproc, mpz_t result);
colproc_compute_and_clear_t colproc_compute_and_clear;

typedef bool colproc_clear_t(handle_t h_colproc);
colproc_clear_t colproc_clear;

typedef bool remove_colproc_t(handle_t h_colproc);
remove_colproc_t remove_colproc;

} // extern "C"

query_t *lookup_query(handle_t h_query);

#endif
