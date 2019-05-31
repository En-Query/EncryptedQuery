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

#include "gpucolproc.h"

#include "maxheap_defs_derooij.h"
#include "derooij.h"
#include "utils.h"
#include "cuda_resp.h"

#include <cassert>
#include <cstdint>
#include <iostream>
#include <map>
#include <memory>
#include <mutex>
#include <thread>
#include <utility>

#include <unistd.h>

using namespace std;

////////////////////////////////////////////////////////////

std::mutex query_mutex;
static handle_t next_query_handle = 1;
std::map<handle_t,query_t*> queries;

////////////////////////////////////////////////////////////

static std::mutex colproc_mutex;
static handle_t next_colproc_handle = 1;
static std::map<handle_t,colproc_t*> colprocs;

////////////////////////////////////////////////////////////

pool_t<device_t> device_pool(DEFAULT_BUSY_POLICY);
static int detect_gpus();
static bool create_gpu_objects();
static void destroy_gpu_objects();

static int detect_gpus() {
  std::thread::id this_id = std::this_thread::get_id();
  int num_gpus;
  cudaGetDeviceCount(&num_gpus);
  COUT_BEGIN;
  cout << "thread " << this_id << " : ";
  cout << "detected " << num_gpus << " GPUs:" << endl;
  COUT_END;
  for (int gpu_id = 0; gpu_id < num_gpus; gpu_id++) {
    cudaDeviceProp prop;
    cudaGetDeviceProperties(&prop, gpu_id);
    COUT_BEGIN;
    cout << "thread " << this_id << " : ";
    cout << "device number: " << gpu_id << endl;
    cout << "thread " << this_id << " : ";
    cout << "  Device name: " << prop.name << endl;
    cout << "thread " << this_id << " : ";
    cout << "  Memory Clock Rate (KHz): " << prop.memoryClockRate << endl;
    cout << "thread " << this_id << " : ";
    cout << "  Memory Bus Width (bits): " << prop.memoryBusWidth << endl;
    cout << "thread " << this_id << " : ";
    cout << "  Peak Memory Bandwidth (GB/s): " << 2.0*prop.memoryClockRate*(prop.memoryBusWidth/8)/1.0e6 << endl;
    COUT_END;
  }
  COUT_BEGIN;
  cout << "thread " << this_id << " : ";
  cout << "finished detecting GPUs" << endl;
  COUT_END;
  return num_gpus;
}

static bool create_gpu_objects() {
  std::thread::id this_id = std::this_thread::get_id();
  int num_gpus;
  cudaGetDeviceCount(&num_gpus);
  COUT_BEGIN;
  cout << "thread " << this_id << " : ";
  cout <<  "creating contexts for " << num_gpus << " GPUS" << endl;
  COUT_END;
  if (device_pool.size() > 0) {
    COUT_BEGIN;
    cout << "thread " << this_id << " : ";
    cout <<  "failed, contexts already exist" << endl;
    COUT_END;
    return false;
  }
  for (int gpu_id = 0; gpu_id < num_gpus; gpu_id++) {
      shared_ptr<device_t> device(new device_t(gpu_id, DEVICE_W, BATCH_SIZE));
      if (!device) return false;
      COUT_BEGIN;
      cout << "thread " << this_id << " : ";
      cout << "created device context " << static_cast<void*>(device.get()) << endl;
      COUT_END;
      device_pool.insert(device);
  }
  COUT_BEGIN;
  cout << "thread " << this_id << " : ";
  cout << "finished creating GPU contexts" << endl;
  COUT_END;
  return true;
}

static void destroy_gpu_objects() {
    std::thread::id this_id = std::this_thread::get_id();
    COUT_BEGIN;
    cout << "thread " << this_id << " : ";
    cout << "destroying contexts for " << device_pool.size() << " GPUS" << endl;
    COUT_END;
    device_pool.clear();
    COUT_BEGIN;
    cout << "thread " << this_id << " : ";
    cout << "finished destroying GPU contexts" << endl;
    COUT_END;
}

static std::mutex lib_mutex;
bool lib_initialized = false;

bool initialize_native_library() {
    std::thread::id this_id = std::this_thread::get_id();
    std::lock_guard<std::mutex> guard(lib_mutex);
    if (lib_initialized) return true;
    COUT_BEGIN;
    cout << "thread " << this_id << " : ";
    cout << "initialize_native_library()" << endl;
    cout << "thread " << this_id << " : ";
    cout << "initializing GPU contexts" << endl;
    COUT_END;
    detect_gpus();
    bool success = create_gpu_objects();
    lib_initialized = true;
    COUT_BEGIN;
    cout << "thread " << this_id << " : ";
    cout << "finished initializing GPU contexts" << endl;
    cout << "thread " << this_id << " : ";
    cout << "leaving initialize_native_library()" << endl;
    COUT_END;
    return success;
}

bool close_native_library() {
    std::thread::id this_id = std::this_thread::get_id();
    std::lock_guard<std::mutex> guard(lib_mutex);
    if (!lib_initialized) return true;
    COUT_BEGIN;
    cout << "thread " << this_id << " : ";
    cout << "close_native_library()" << endl;
    COUT_END;
    // TODO: destroy any remaining query and column processor contexts
    destroy_gpu_objects();
    lib_initialized = false;
    bool success = true;
    COUT_BEGIN;
    cout << "thread " << this_id << " : ";
    cout << "leaving close_native_library()" << endl;
    COUT_END;
    return success;
}

bool set_busy_policy(policy_t policy) {
    std::lock_guard<std::mutex> guard(lib_mutex);
    device_pool.set_policy(policy);
    return true;
}

////////////////////////////////////////////////////////////

query_t *lookup_query(handle_t h_query) {
  auto it = queries.find(h_query);
  query_t *q;
  if (it != queries.end()) {
    return it->second;
  } else {
    q = NULL;
  }
  return q;
}

colproc_t *lookup_colproc(handle_t h_colproc) {
  auto it = colprocs.find(h_colproc);
  colproc_t *c;
  if (it != colprocs.end()) {
    return it->second;
  } else {
    c = NULL;
  }
  return c;
}

////////////////////////////////////////////////////////////

handle_t create_query(const string &uuid, mpz_t N, size_t num_qelts) {
  std::thread::id this_id = std::this_thread::get_id();
  //COUT_BEGIN;
  //cout << "create_query() called " << endl;
  //COUT_END;

  const size_t w = DEVICE_W;
  if (mpz_sizeinbase(N,2) > w*32) return 0;

  // See if a query corresponding to the given UUID already exists.
  bool exists = false;
  handle_t handle;
  {
    std::lock_guard<std::mutex> guard(query_mutex);
    for (auto it=queries.begin(); it!=queries.end(); it++) {
      if (uuid != "" && it->second->uuid == uuid) {
	COUT_BEGIN;
	cout << "thread " << this_id << " : ";
	cout << "found existing query for UUID " << uuid << endl;
	COUT_END;
	exists = true;
	handle = it->first;
	it->second->ref_count++;
	break;
      }
    }
    if (!exists) {
      COUT_BEGIN;
      cout << "thread " << this_id << " : ";
      cout << "creating new query object for UUID " << uuid << endl;
      COUT_END;
      handle = next_query_handle++;
      queries[handle] = new query_t(uuid, w, N, num_qelts);
      queries[handle]->ref_count++;
    }
  }
  return handle;
}

bool query_set_qelt(handle_t h_query, size_t row, mpz_t qelt) {
  std::lock_guard<std::mutex> guard(query_mutex);
  query_t *query = lookup_query(h_query);
  if (NULL == query) return false;
  if (row >= query->num_qelts) return false;
  query->set_qelt(row, qelt);
}

bool remove_query(handle_t h_query) {
  std::thread::id this_id = std::this_thread::get_id();
  COUT_BEGIN;
  cout << "thread " << this_id << " : ";
  cout << "remove_query()" << endl;
  COUT_END;
  std::lock_guard<std::mutex> mutex_guard(query_mutex);
  auto it = queries.find(h_query);
  if (it == queries.end()) {
    // query not found
    return false;
  }
  query_t *query = it->second;
  if (!query) return false; // shouldn't happen
  query->ref_count--;
  //COUT_BEGIN;
  //cout << "thread " << this_id << " : ";
  //cout << "decremented reference count, now equals " << query->ref_count << endl;
  //COUT_END;

  // is someone else still using this query?
  if (query->ref_count > 0) {
      return true;
  }

  COUT_BEGIN;
  //cout << "thread " << this_id << " : ";
  //cout << "reference count zero" << endl;
  cout << "thread " << this_id << " : ";
  cout << "removing associated column processors" << endl;
  COUT_END;
      
  // first, erase any remaining associated column processors
  {
    std::lock_guard<std::mutex> colproc_guard(colproc_mutex);
    auto it2 = colprocs.begin();
    while (it2 != colprocs.end()) {
      if (it2->second->query == query) {
	//COUT_BEGIN;
	//cout << "thread " << this_id << " : ";
	//cout << "  removing colproc" << endl;
	//COUT_END;
	delete it2->second;
	it2 = colprocs.erase(it2);
      } else {
	it2++;
      }
    }
  }

  // now we can erase the query
  COUT_BEGIN;
  cout << "thread " << this_id << " : ";
  cout << "deleting the query" << endl;
  COUT_END;
  delete query;
  queries.erase(it);

  COUT_BEGIN;
  cout << "thread " << this_id << " : ";
  cout << "leaving remove_query()" << endl;
  COUT_END;
  return true;
}

handle_t create_colproc(handle_t h_query) {
  std::thread::id this_id = std::this_thread::get_id();
  COUT_BEGIN;
  cout << "thread " << this_id << " : ";
  cout << "create_colproc()" << endl;
  COUT_END;
  query_t *query = lookup_query(h_query);
  if (query == NULL) {
    return HANDLE_ERROR;
  }
  handle_t h_colproc;
  {
    std::lock_guard<std::mutex> guard(colproc_mutex);
    h_colproc = next_colproc_handle++;
    colprocs[h_colproc] = new colproc_t(query, BATCH_SIZE);
  }
  return h_colproc;
}

bool remove_colproc(handle_t h_colproc) {
  std::thread::id this_id = std::this_thread::get_id();
  COUT_BEGIN;
  cout << "thread " << this_id << " : ";
  cout << "remove_colproc()" << endl;
  COUT_END;
  std::lock_guard<std::mutex> guard(colproc_mutex);
  auto it = colprocs.find(h_colproc);
  bool ret = false;
  if (it != colprocs.end()) {
    // found
    delete it->second;
    colprocs.erase(it);
    ret = true;
  } else {
    // not found
  }
  return ret;
}

////////////////////////////////////////////////////////////

bool colproc_insert_chunk(handle_t h_colproc, size_t row, uint32_t chunk) {
  colproc_t *colproc = lookup_colproc(h_colproc);
  if (colproc) {
    return colproc->insert_chunk(row, chunk);
  } else {
    return false;
  }
}

// returns 0 for success, nonzero for various types of errors
int colproc_compute_and_clear(handle_t h_colproc, mpz_t result) {
    std::thread::id this_id = std::this_thread::get_id();

    // get column processor object
    colproc_t *colproc;
    {
	std::lock_guard<std::mutex> guard(colproc_mutex);
	colproc = lookup_colproc(h_colproc);
    }
    if (!colproc) return 1;

    // get GPU or CPU device or abort, per policy
    //COUT_BEGIN;
    //cout << "thread " << this_id << " : ";
    //cout << "XXX calling device_pool.request()" << endl;
    //COUT_END;
    resource_t<device_t> &r = device_pool.request();
    //COUT_BEGIN;
    //cout << "thread " << this_id << " : ";
    //cout << "XXX returned from device_pool.request()" << endl;
    //COUT_END;
    shared_ptr<device_t> p_device = r.get();
    device_t *device;
    if (p_device) {
	// get raw pointer
	device = p_device.get();
    } else {
	switch(device_pool.policy) {
	case RP_POLICY_WAIT:
	    // unexpected case, since request() is expected to return
	    // a non-null resource for POLICY_WAIT
	    COUT_BEGIN;
	    cout << "thread " << this_id << " : ";
	    cout << "unexpected error while acquiring free GPU" << endl;
	    COUT_END;
	    return 2;
	case RP_POLICY_CALLER_RUNS:
	    //COUT_BEGIN;
	    //cout << "thread " << this_id << " : ";
	    //cout << "XXX caller runs case" << endl;
	    //COUT_END;
	    device = new device_t(-1, DEVICE_W, BATCH_SIZE);
	    if (!device) {
		COUT_BEGIN;
		cout << "thread " << this_id << " : ";
		cout << "failed to create CPU context" << endl;
		COUT_END;
		return 3;
	    }
	    break;
	case RP_POLICY_ABORT:
	    COUT_BEGIN;
	    cout << "thread " << this_id << " : ";
	    cout << "all GPUs busy, abort per policy" << endl;
	    COUT_END;
	    // no GPU currenly available, fail as per policy
	    return 4;
	case RP_POLICY_GPU_NOW:
	    //COUT_BEGIN;
	    //cout << "thread " << this_id << " : ";
	    //cout << "GPU now!" << endl;
	    //COUT_END;
	    p_device = colproc->device;
	    device = p_device.get();
	    break;
	}
    }

    //COUT_BEGIN;
    //cout << "thread " << this_id << " : ";
    //cout << "obtained device " << static_cast<void*>(device) << " (" << (bool)(p_device) << ")" << endl;
    //COUT_END;

    // do work
    //cout << "computing with device " << device->device << endl;
    device->set_modulus(*(colproc->query));
    uint64_t compute_start = time_now();
    bool bstatus = colproc->compute_and_clear(*device, result);
    int status = bstatus ? 0 : 5;
    uint64_t compute_time = time_now() - compute_start;
    //COUT_BEGIN;
    //cout << "thread " << this_id << " : ";
    //cout << "compute operation on device " << device-> device << " took " << compute_time << " ns" << endl;
    //COUT_END;
    
    // release GPU or CPU device
    //COUT_BEGIN;
    //cout << "thread " << this_id << " : ";
    //cout << "releasing device " << static_cast<void*>(device) << endl;
    //COUT_END;

    // if we didn't get device from the pool, we destroy it now
    device_pool.release(r);
    if (!p_device) {
	delete device;
    }

    return status;
}

bool colproc_clear(handle_t h_colproc) {
  colproc_t *colproc = lookup_colproc(h_colproc);
  if (colproc) {
    return colproc->clear();
  } else {
    return false;
  }
}

////////////////////////////////////////////////////////////

std::mutex roundrobin_mutex;
int roundrobin_next = 0;

colproc_t::colproc_t(query_t *query, size_t batch_size) :
  query(query), yao(*query, batch_size) {
    derooij_init(&(this->derooij_ctx), query->N2z, query->num_qelts);
    int num_gpus = (int)device_pool.size();
    if (num_gpus > 0) {
	this->device = shared_ptr<device_t>(new device_t(roundrobin_next, DEVICE_W, BATCH_SIZE));
	roundrobin_next = (roundrobin_next + 1) % num_gpus;
    } else {
	this->device = shared_ptr<device_t>(new device_t(-1, DEVICE_W, BATCH_SIZE));
    }
}

colproc_t::~colproc_t() {
  derooij_fini(&(this->derooij_ctx));
}

bool colproc_t::insert_chunk(size_t row, uint32_t chunk) {
  this->yao.insert_chunk(row, (binnum_t)chunk);
  return true;
}

static void compute_and_clear_with_derooijjni(query_t &query, colproc_t &colproc, yao_t &yao, mpz_t out);

bool colproc_t::compute_and_clear(device_t &device, mpz_t result) {
  if (device.device == -1) {
    // use DeRooijJNI instead
    compute_and_clear_with_derooijjni(*this->query, *this, this->yao, result);
    this->yao.clear();
    return true;
  }
  this->yao.compute(device, result, 0);
  this->yao.clear();
  return true;
}

bool colproc_t::clear() {
  this->yao.clear();
  return true;
}

static void compute_and_clear_with_derooijjni(query_t &query, colproc_t &colproc, yao_t &yao, mpz_t out) {
  //std::thread::id this_id = std::this_thread::get_id();
  derooij_ctx_t *ctx = &colproc.derooij_ctx;
  //COUT_BEGIN;
  //cout << "thread " << this_id << " : ";
  //cout << "XXX using derooij" << endl;
  //COUT_END;
  mpz_t part;
  mpz_init(part);
  for (auto ibin=yao.bins.l.begin(); ibin!=yao.bins.l.end(); ibin++) {
    bin_t &bin = *ibin;
    mpz_set_ui(part, (unsigned long)bin.binnum);
    for (auto ichunk=bin.r.begin(); ichunk!=bin.r.end(); ichunk++) {
      size_t row = *ichunk;
      assert (row < query.num_qelts);
      derooij_insert_data_part2(ctx, query.query_elements[row], part);
    }
  }
  derooij_compute_column_and_clear_data(ctx, out);
  mpz_clear(part);
}
