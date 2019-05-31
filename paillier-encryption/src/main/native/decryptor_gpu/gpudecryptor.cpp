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

#include "gpudecryptor.h"
#include "resources.h"

#include "xmp.h"
#include <cuda_runtime_api.h>

#include <gmp.h>

#include <chrono>
#include <cstdint>
#include <iomanip>
#include <iostream>
#include <map>
#include <mutex>
#include <thread>
#include <stdexcept>
#include <vector>
#define NDEBUG
#include <assert.h>
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define DEFAULT_BATCH_SIZE 4096

using namespace std;


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

////////////////////////////////////////////////////////////

std::mutex cm;

////////////////////////////////////////////////////////////

static std::mutex ctx_mutex;
static handle_t next_ctx_handle = 1;
static std::map<handle_t,gpudecryptor_ctx_t*> contexts;

////////////////////////////////////////////////////////////

pool_t<device_t> device_pool(DEFAULT_BUSY_POLICY);
static void detect_gpus();
static bool create_gpu_objects();
static void destroy_gpu_objects();

static void detect_gpus() {
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
}

static bool create_gpu_objects() {
    std::thread::id this_id = std::this_thread::get_id();
    COUT_BEGIN;
    cout << "thread " << this_id << " : ";
    cout << "detecting GPUs" << endl;
    COUT_END;
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
	shared_ptr<device_t> device(new device_t(gpu_id, DEFAULT_BATCH_SIZE));
	if (!device) return false;
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

////////////////////////////////////////////////////////////

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
    {
	std::lock_guard<std::mutex> guard(ctx_mutex);
	COUT_BEGIN;
	cout << "thread " << this_id << " : ";
	cout << "destroying any remaining query contexts" << endl;
	COUT_END;
	auto it = contexts.begin();
	while (it != contexts.end()) {
	    delete it->second;
	    it = contexts.erase(it);
	}
    }
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

size_t mpz_bytes_needed(mpz_t z)
{
    int sgn = mpz_sgn(z);
    assert (sgn >= 0);
    if (sgn == 0) {
	// need to handle the special case z == 0 separately
	return 0;
    } else {
	return (mpz_sizeinbase(z,2) + 7) / 8;
    }
}

void mpz_from_be(mpz_t rop, const uint8_t bytes[], int num_bytes)
{
    mpz_import(rop, num_bytes, 1, 1, -1, 0, bytes);
}

void mpz_from_le(mpz_t rop, const uint8_t bytes[], int num_bytes)
{
    mpz_import(rop, num_bytes, -1, 1, -1, 0, bytes);
}

void le_from_mpz(uint8_t bytes[], int num_bytes, mpz_t z)
{
    size_t countp;
    mpz_export(bytes, &countp, -1/*little endian*/, 1, -1, 0, z);
    assert (countp <= num_bytes);
    if (countp < num_bytes) {
	memset(bytes + countp, 0, num_bytes-countp);
    }
}

void be_from_mpz(uint8_t bytes[], int num_bytes, mpz_t z)
{
    size_t needed = mpz_bytes_needed(z);
    assert (needed <= num_bytes);
    size_t padding, countp;
    padding = num_bytes - needed;
    memset(bytes, 0, padding);
    mpz_export(bytes+padding, &countp, 1/*big endian*/, 1, -1, 0, z);
    assert (countp == needed);
}

/* Converts a single mpz into an XMP integer with count==1.
*/
int xi_from_mpz(xmpHandle_t handle, xmpIntegers_t xi, mpz_t z)
{
    int success = 0;
    int num_bits = mpz_sizeinbase(z,2);

    uint32_t precision;
    XMP_CHECK_ERROR(success, xmpIntegersGetPrecision(handle, xi, &precision));
    if (!success) {
	return 0;
    }
    if (num_bits > (int)precision) {
	std::thread::id this_id = std::this_thread::get_id();
	COUT_BEGIN;
	cout << "thread " << this_id << " : ";
	cout << "input too big!" << endl;
	COUT_END;
	return 0;
    }
    size_t num_bytes = ((size_t)precision +  7) / 8;
    vector<uint8_t> buf(num_bytes);
    size_t countp;
    mpz_export(buf.data(), &countp, -1, 1, -1, 0, z);
    assert (countp <= num_bytes);
    if (countp < num_bytes) {
	memset(buf.data()+countp, 0, (num_bytes-countp));
    }
    XMP_CHECK_ERROR(success, xmpIntegersImport(handle, xi, num_bytes, -1, 1, -1, 0, buf.data(), 1));
 done:
    return success;
}

////////////////////////////////////////////////////////////

gpudecryptor_ctx_t *lookup_context(handle_t h_context) {
  auto it = contexts.find(h_context);
  gpudecryptor_ctx_t *ctx;
  if (it != contexts.end()) {
    ctx = it->second;
  } else {
    ctx = NULL;
  }
  return ctx;
}

////////////////////////////////////////////////////////////

device_t::device_t(int gpu_id, int batch_size)
: gpu_id(gpu_id), busy(false), batch_size(batch_size)
{
    std::thread::id this_id = std::this_thread::get_id();
    COUT_BEGIN;
    cout << "thread " << this_id << " : ";
    cout << "initializing context for device " << gpu_id << endl;
    COUT_END;
    if (gpu_id >= 0) {
	bool success = true;
	xmpbuf_p.resize(batch_size * PT_BYTES);
	xmpbuf_q.resize(batch_size * PT_BYTES);
	cudaDeviceProp prop;
	cudaGetDeviceProperties(&prop, gpu_id);
	COUT_BEGIN;
	cout << "thread " << this_id << " : ";
	cout << "device name: " << prop.name << endl;
	COUT_END;
	cudaSetDevice(gpu_id);
	bool succ;
	XMP_CHECK_ERROR(succ, xmpHandleCreate(&handle));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&xiOut,PT_BYTES*8,batch_size));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&xiBase,PT_BYTES*8,batch_size));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&xiP1,P1_BYTES*8,1));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&xiQ1,P1_BYTES*8,1));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&xiP2,PT_BYTES*8,1));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&xiQ2,PT_BYTES*8,1));
	success &= succ;
	if (!success) {
	    throw std::runtime_error("failed to initialize XMP objects");
	}
    }	
}

device_t::~device_t() {
    std::thread::id this_id = std::this_thread::get_id();
    COUT_BEGIN;
    cout << "thread " << this_id << " : ";
    cout << "finalizing context for device " << gpu_id << endl;
    COUT_END;
    if (gpu_id) {
	bool succ;
	XMP_CHECK_ERROR(succ, xmpIntegersDestroy(handle,xiOut));
	if (!succ) return;
	XMP_CHECK_ERROR(succ, xmpIntegersDestroy(handle,xiBase));
	if (!succ) return;
	XMP_CHECK_ERROR(succ, xmpIntegersDestroy(handle,xiP1));
	if (!succ) return;
	XMP_CHECK_ERROR(succ, xmpIntegersDestroy(handle,xiQ1));
	if (!succ) return;
	XMP_CHECK_ERROR(succ, xmpIntegersDestroy(handle,xiP2));
	if (!succ) return;
	XMP_CHECK_ERROR(succ, xmpIntegersDestroy(handle,xiQ2));
	if (!succ) return;
	XMP_CHECK_ERROR(succ, xmpHandleDestroy(handle));
	if (!succ) return;
    }
}


////////////////////////////////////////////////////////////

gpudecryptor_ctx_t::gpudecryptor_ctx_t(mpz_t p, mpz_t q, mpz_t p1, mpz_t q1) {
    mpz_init_set(this->p, p);
    mpz_init_set(this->q, q);
    mpz_init_set(this->p1, p1);
    mpz_init_set(this->q1, q1);

    // p^2, q^2, N = p*q
    mpz_init(this->p2);
    mpz_mul(this->p2, p, p);
    mpz_init(this->q2);
    mpz_mul(this->q2, q, q);
    mpz_init(this->N);
    mpz_mul(this->N, p, q);

    // wp = (p1 * q)^-1 mod p
    // wq = (q1 * p)^-1 mod q
    mpz_init(this->wp);
    mpz_mul(this->wp, p1, q);
    mpz_invert(this->wp, this->wp, p);
    mpz_init(this->wq);
    mpz_mul(this->wq, q1, p);
    mpz_invert(this->wq, this->wq, q);

    // 1 = gcd = ap * p + aq * q
    // crt = ap * p  (0 mod p, 1 mod q)
    mpz_init(this->crt);
    mpz_t gcd, ap, aq;
    mpz_inits(gcd, ap, aq, NULL);
    mpz_gcdext(gcd, ap, aq, p, q);
    //assert (!mpz_cmp_ui(gcd, 1));
    mpz_mul(this->crt, ap, p);
    mpz_clears(gcd, ap, aq, NULL);

    this->plaintext_byte_size = (mpz_sizeinbase(this->N, 2) + 7) / 8;
    mpz_t N2;
    mpz_init(N2);
    mpz_mul(N2, this->N, this->N);
    this->ciphertext_byte_size = (mpz_sizeinbase(N2, 2) + 7) / 8;
}

gpudecryptor_ctx_t::~gpudecryptor_ctx_t() {
    mpz_clears(this->p, this->q, this->p1, this->q1, NULL);
    mpz_clears(this->p2, this->q2, this->N, NULL);
    mpz_clears(this->wp, this->wq, this->crt, NULL);
}

////////////////////////////////////////////////////////////

handle_t gpudecryptor_new(mpz_t p, mpz_t q, mpz_t p1, mpz_t q1)
{
    if (mpz_sizeinbase(p, 2) > PT_BYTES*8/2
	|| mpz_sizeinbase(q, 2) > PT_BYTES*8/2
	|| mpz_sizeinbase(p1, 2) > P1_BYTES*8
	|| mpz_sizeinbase(q1, 2) > P1_BYTES*8) {
	std::thread::id this_id = std::this_thread::get_id();
	COUT_BEGIN;
	cout << "thread " << this_id << " : ";
	cout << "key length too large!" << endl;
	COUT_END;
	return HANDLE_ERROR;
    }

    gpudecryptor_ctx_t *ctx = new gpudecryptor_ctx_t(p, q, p1, q1);
    if (NULL == ctx) {
	return HANDLE_ERROR;
    }
    handle_t h_context;
    {
	std::lock_guard<std::mutex> guard(ctx_mutex);
	h_context = next_ctx_handle;
	contexts[next_ctx_handle++] = ctx;
    }
    return h_context;
}

void gpudecryptor_delete(handle_t h_context)
{
    std::lock_guard<std::mutex> guard(ctx_mutex);
    gpudecryptor_ctx_t *ctx = lookup_context(h_context);
    if (ctx) delete ctx;
    contexts.erase(h_context);
}

// x = c^pMaxExponent mod p^2, y = (x - 1)/p, z = y * (pMaxExponent*q)^-1 mod p
// x' = c^qMaxExponent mod q^2, y' = (x'- 1)/q, z' = y' * (qMaxExponent*p)^-1 mod q
// d = crt.combine(z, z')
static
void decrypt_cpu(gpudecryptor_ctx_t *ctx, mpz_t out, mpz_t ciphertext)
{
    mpz_t t, zp; 
    mpz_inits(t, zp, NULL);
    // xp = c^p1 mod p^2
    mpz_fdiv_r(t, ciphertext, ctx->p2);
    mpz_powm(t, t, ctx->p1, ctx->p2);
    // yp = (xp - 1)/p
    mpz_sub_ui(t, t, 1);
    mpz_fdiv_q(t, t, ctx->p);
    // zp = yp * (p1*q)^-1 mod p
    mpz_mul(t, t, ctx->wp);
    mpz_fdiv_r(zp, t, ctx->p);
    // xq = c^q1 mod q^2
    mpz_fdiv_r(t, ciphertext, ctx->q2);
    mpz_powm(t, t, ctx->q1, ctx->q2);
    // yq = (xq - 1)/q
    mpz_sub_ui(t, t, 1);
    mpz_fdiv_q(t, t, ctx->q);
    // zq = yq * (q1*p)^-1 mod q
    mpz_mul(t, t, ctx->wq);
    mpz_fdiv_r(t, t, ctx->q);
    // out = crt.combine(zp, zq) = zp + (zq - zp) * crt
    mpz_sub(out, t, zp);
    mpz_mul(out, out, ctx->crt);
    mpz_add(out, out, zp);
    mpz_fdiv_r(out, out, ctx->N);
    mpz_clears(t, zp, NULL);
}


int decrypt_batch_gpu(gpudecryptor_ctx_t *ctx, device_t *device, uint8_t* buf, int num_ciphs);
int decrypt_batch_cpu(gpudecryptor_ctx_t *ctx, uint8_t* buf, int num_ciphs);

// returns 0 for success, nonzero for various types of errors
int gpudecryptor_decrypt_batch(handle_t h_context, uint8_t* buf, int num_ciphs, bool use_gpu)
{
    std::thread::id this_id = std::this_thread::get_id();
    COUT_BEGIN;
    std::cout << "thread " << this_id << " : ";
    std::cout << "gpudecryptor_decrypt_batch()" << endl;
    COUT_END;
    gpudecryptor_ctx_t *ctx;
    {
	std::lock_guard<std::mutex> guard(ctx_mutex);
	ctx = lookup_context(h_context);
    }
    if (NULL == ctx) {
	//COUT_BEGIN;
	//cout << "thread " << this_id << " : ";
	//cout << "XXX gpudecryptor_decrypt_batch returning " << 1 << endl;
	//COUT_END;
	return 1; // FAIL
    }
    if (use_gpu) {
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
	device_t *device = NULL;;
	if (p_device) {
	    // get raw pointer
	    device = p_device.get();
	    //COUT_BEGIN;
	    //std::cout << "thread " << this_id << " : ";
	    //std::cout << "got free GPU, gpu_id=" << device->gpu_id << std::endl;
	    //COUT_END;
	} else {
	    //COUT_BEGIN;
	    //std::cout << "thread " << this_id << " : ";
	    //std::cout << "did not get free GPU" << std::endl;
	    //COUT_END;
	    switch(device_pool.policy) {
	    case RP_POLICY_WAIT:
		// unexpected case, since request() is expected to return
		// a non-null resource for POLICY_WAIT
		COUT_BEGIN;
		cout << "thread " << this_id << " : ";
		cout << "unexpected error while acquiring GPU" << endl;
		COUT_END;
		return 2;
	    case RP_POLICY_CALLER_RUNS:
		//COUT_BEGIN;
		//cout << "thread " << this_id << " : ";
		//cout << "XXX caller runs case" << endl;
		//COUT_END;
		// will use CPU
		break;
	    case RP_POLICY_ABORT:
		COUT_BEGIN;
		cout << "thread " << this_id << " : ";
		cout << "all GPUs busy, aborting as per policy" << endl;
		COUT_END;
		// no GPU currenly available, fail as per policy
		return 4;
	    }
	}

	// now execute
	int success;
	if (device) {
	    success = decrypt_batch_gpu(ctx, device, buf, num_ciphs) ? 0 : 5;
	} else {
	    success = decrypt_batch_cpu(ctx, buf, num_ciphs) ? 0 : 5;
	}	    

	// done, release device
	if (device) {
	    device_pool.release(r);
	}

	//COUT_BEGIN;
	//cout << "thread " << this_id << " : ";
	//cout << "XXX gpudecryptor_decrypt_batch returning " << success << endl;
	//COUT_END;
	return success;
    } else {
	return decrypt_batch_cpu(ctx, buf, num_ciphs) ? 0 : 5;
    }
}

int decrypt_batch_cpu(gpudecryptor_ctx_t *ctx, uint8_t* buf, int num_ciphs)
{
    std::thread::id this_id = std::this_thread::get_id();
    COUT_BEGIN;
    cout << "thread " << this_id << " : ";
    cout << "decrypt_batch_cpu()" << endl;
    COUT_END;
    int success = 0;

    mpz_t ciphertext, plaintext;
    mpz_inits(ciphertext, plaintext, NULL);

    int count = 0;
    uint8_t *outbuf = buf;
    int n;

    auto begin = std::chrono::high_resolution_clock::now();
    for (int remaining = num_ciphs;
	 remaining > 0;
	 remaining -= n,
	     buf += n * ctx->ciphertext_byte_size,
	     outbuf += n * ctx->plaintext_byte_size,
	     count += n
	 ) {

	n = DEFAULT_BATCH_SIZE;
	if (remaining < n) {
	    n = remaining;
	}

	for (int i = 0; i < n; i++) {
	    mpz_from_be(ciphertext, buf+i*ctx->ciphertext_byte_size, ctx->ciphertext_byte_size);
	    decrypt_cpu(ctx, plaintext, ciphertext);
	    be_from_mpz(outbuf+i*ctx->plaintext_byte_size, ctx->plaintext_byte_size, plaintext);
	}
    }
    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::nanoseconds>(end-begin).count();
    std::cout << __func__ << ": total decryption time:      " << std::setw(15) << duration << " nanoseconds to do " << num_ciphs << " ciphertexts" << std::endl;
    std::cout << __func__ << ": (" << (double)num_ciphs*1e9/duration << " decryptions per second)" << std::endl;

    mpz_clears(ciphertext, plaintext, NULL);

    success = 1;
    return success;
}

int decrypt_batch_gpu(gpudecryptor_ctx_t *ctx, device_t *device, uint8_t* buf, int num_ciphs)
{
    std::thread::id this_id = std::this_thread::get_id();
    COUT_BEGIN;
    cout << "thread " << this_id << " : ";
    cout << "decrypt_batch_gpu() with gpu_id=" << device->gpu_id << endl;
    COUT_END;
    int success = 0;

    mpz_t ciphertext, cmod;
    mpz_inits(ciphertext, cmod, NULL);

    mpz_t t, zp;
    mpz_inits(t, zp, NULL);

    int count = 0;
    uint8_t *outbuf = buf;
    int n;

    if (!xi_from_mpz(device->handle, device->xiP1, ctx->p1)
	|| !xi_from_mpz(device->handle, device->xiQ1, ctx->q1)
	|| !xi_from_mpz(device->handle, device->xiP2, ctx->p2)
	|| !xi_from_mpz(device->handle, device->xiQ2, ctx->q2)) {
	COUT_BEGIN;
	cout << "thread " << this_id << " : ";
	cout << "error importing private key into device!" << endl;
	COUT_END;
	return 0;
    }
    uint8_t *xmpbuf_p = device->xmpbuf_p.data();
    uint8_t *xmpbuf_q = device->xmpbuf_q.data();

    std::chrono::nanoseconds split_duration(0);
    std::chrono::nanoseconds powm_duration(0);
    std::chrono::nanoseconds rest_duration(0);
    auto begin = std::chrono::high_resolution_clock::now();
    for (int remaining = num_ciphs;
	 remaining > 0;
	 remaining -= n,
	     buf += n * ctx->ciphertext_byte_size,
	     outbuf += n * ctx->plaintext_byte_size,
	     count += n
	 ) {

	n = device->batch_size;
	if (remaining < n) {
	    n = remaining;
	}

	// split each ciphertext C into C mod p^2 and C mod q^2
	auto split_begin = std::chrono::high_resolution_clock::now();
	for (int i = 0; i < n; i++) {
	    mpz_from_be(ciphertext, buf+i*ctx->ciphertext_byte_size, ctx->ciphertext_byte_size);
	    mpz_fdiv_r(cmod, ciphertext, ctx->p2);
	    le_from_mpz(xmpbuf_p+i*PT_BYTES, PT_BYTES, cmod);
	    mpz_fdiv_r(cmod, ciphertext, ctx->q2);
	    le_from_mpz(xmpbuf_q+i*PT_BYTES, PT_BYTES, cmod);
	}
	auto split_end = std::chrono::high_resolution_clock::now();
	split_duration += std::chrono::duration_cast<std::chrono::nanoseconds>(split_end-split_begin);

	// compute modular powers using the GPU
	bool succ;
	uint32_t words;
	auto pow1_begin = std::chrono::high_resolution_clock::now();
	XMP_CHECK_ERROR(succ, xmpIntegersImport(device->handle, device->xiBase, PT_BYTES, -1, 1, -1, 0, xmpbuf_p, n));
	if (!succ) return 0;
	XMP_CHECK_ERROR(succ, xmpIntegersPowm(device->handle,device->xiOut,device->xiBase,device->xiP1,device->xiP2,n));
	if (!succ) return 0;
	XMP_CHECK_ERROR(succ, xmpIntegersExport(device->handle,xmpbuf_p,&words,-1,1,-1,0,device->xiOut,n));
	if (!succ) return 0;
	auto pow1_end = std::chrono::high_resolution_clock::now();
	powm_duration += std::chrono::duration_cast<std::chrono::nanoseconds>(pow1_end-pow1_begin);
	assert (words == PT_BYTES); // number of words per integer
	auto pow2_begin = std::chrono::high_resolution_clock::now();
	XMP_CHECK_ERROR(succ, xmpIntegersImport(device->handle, device->xiBase, PT_BYTES, -1, 1, -1, 0, xmpbuf_q, n));
	if (!succ) return 0;
	XMP_CHECK_ERROR(succ, xmpIntegersPowm(device->handle,device->xiOut,device->xiBase,device->xiQ1,device->xiQ2,n));
	if (!succ) return 0;
	XMP_CHECK_ERROR(succ, xmpIntegersExport(device->handle,xmpbuf_q,&words,-1,1,-1,0,device->xiOut,n));
	if (!succ) return 0;
	auto pow2_end = std::chrono::high_resolution_clock::now();
	powm_duration += std::chrono::duration_cast<std::chrono::nanoseconds>(pow2_end-pow2_begin);
	assert (words == PT_BYTES); // number of words per integer

	// complete the rest of the decryption operations
	auto rest_begin = std::chrono::high_resolution_clock::now();
	for (int i = 0; i < n; i++) {
	    // xp = c^p1 mod p^2
	    mpz_from_le(t, xmpbuf_p+i*PT_BYTES, PT_BYTES);
	    // yp = (xp - 1)/p
	    mpz_sub_ui(t, t, 1);
	    mpz_fdiv_q(t, t, ctx->p);
	    // zp = yp * (p1*q)^-1 mod p
	    mpz_mul(t, t, ctx->wp);
	    mpz_fdiv_r(zp, t, ctx->p);
	    // xq = c^q1 mod q^2
	    mpz_from_le(t, xmpbuf_q+i*PT_BYTES, PT_BYTES);
	    // yq = (xq - 1)/q
	    mpz_sub_ui(t, t, 1);
	    mpz_fdiv_q(t, t, ctx->q);
	    // zq = yq * (q1*p)^-1 mod q
	    mpz_mul(t, t, ctx->wq);
	    mpz_fdiv_r(t, t, ctx->q);
	    // out = crt.combine(zp, zq)
	    //    = zp + (zq - zp) * crt
	    mpz_sub(t, t, zp);
	    mpz_mul(t, t, ctx->crt);
	    mpz_add(t, t, zp);
	    mpz_fdiv_r(t, t, ctx->N);
	    // write result to buffer
	    be_from_mpz(outbuf+i*ctx->plaintext_byte_size, ctx->plaintext_byte_size, t);
	}
	auto rest_end = std::chrono::high_resolution_clock::now();
	rest_duration += std::chrono::duration_cast<std::chrono::nanoseconds>(rest_end-rest_begin);
    }
    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::nanoseconds>(end-begin).count();
    COUT_BEGIN;
    std::cout << __func__ << ": total decryption time:      " << std::setw(15) << duration << " nanoseconds to do " << num_ciphs << " ciphertexts" << std::endl;
    std::cout << __func__ << ": (" << (double)num_ciphs*1e9/duration << " decryptions per second)" << std::endl;
    std::cout << __func__ << ":     time spent doing split: " << std::setw(15) << split_duration.count() << " nanoseconds." << std::endl;
    std::cout << __func__ << ":     time spent doing powm:  " << std::setw(15) << powm_duration.count() << " nanoseconds." << std::endl;
    std::cout << __func__ << ":     time spent doing rest:  " << std::setw(15) << rest_duration.count() << " nanoseconds." << std::endl;
    COUT_END;

    mpz_clears(ciphertext, cmod, NULL);
    mpz_clears(t, zp, NULL);

    success = 1;
    return success;
}

