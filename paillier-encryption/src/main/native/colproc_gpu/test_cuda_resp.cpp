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

#include "cuda_resp.h"
#include "utils.h"

#include <gmp.h>

#include <cassert>
#include <cstdlib>
#include <iostream>
#include <random>
#include <unordered_map>

#define USE_GPU

using namespace std;

void basic(query_t &query, mpz_t result, const size_t *data_rows, const uint32_t *data_chunks, size_t num_chunks) {

  const size_t w = query.w;
  const size_t w2 = query.w2;
  const size_t num_qelts = query.num_qelts;
  mpz_t mz;
  mpz_t qz[num_qelts];
  mpz_init(mz);
  mpz_set(mz, query.N2z);
  for (int i = 0; i < num_qelts; i++) {
    mpz_init(qz[i]);
#ifdef USE_RADIXN
    query.mpzt_from_words(qz[i], query.qelts.data()+i*w2, query.qelts.data()+i*w2+w);
#else
    mpz_fdiv_r(qz[i], qz[i], mz);
    query.mpzt_from_words(qz[i], query.qelts.data()+i*w2);
#endif
  }

  // compute answer from scratch using basic method
  {
    mpz_t prod;
    mpz_t tmp;
    mpz_init_set_ui(prod, 1);
    mpz_init(tmp);
    for (size_t i = 0; i < num_chunks; i++) {
      mpz_powm_ui(tmp, qz[i % num_qelts], data_chunks[i], mz);
      mpz_mul(prod, prod, tmp);
      mpz_fdiv_r(prod, prod, mz);
    }
    mpz_set(result, prod);
    mpz_clear(prod);
    mpz_clear(tmp);
  }

  for (int i = 0; i < num_qelts; i++) {
    mpz_clear(qz[i]);
  }
  mpz_clear(mz);

}

void do_test(size_t w, size_t num_qelts, size_t batch_size, size_t max_chunk, size_t num_chunks) {
  const size_t DEV_W = w;
  //const size_t DEV_W = w >= 100 ? w : 100;
  cout << "using DEV_W = " << DEV_W << endl;

  const size_t w2 = 2*w;

  uniform_int_distribution<uint32_t> ud;
  uniform_int_distribution<uint32_t> ud2(0, max_chunk);
  std::default_random_engine generator;

  cout << "------------------------------------------------------------" << endl;
  cout << __func__ << ": ";
  cout << " w = " << w;
  cout << ", num_qelts = " << num_qelts;
  cout << ", batch_size = " << batch_size;
  cout << ", max_chunk = " << max_chunk;
  cout << ", num_chunks = " << num_chunks;
  cout << endl;

  //cout << "some random uint32_t's:" << endl;
  //cout << "u = " << ud(generator) << endl;
  //cout << "u = " << ud(generator) << endl;

  // create simulated query
  uint32_t *N_words = new uint32_t[w];
  for (int i = 0; i < w; i++) {
    N_words[i] = ud(generator);
  }
  N_words[0] |= 1;
  N_words[w-1] |= (uint32_t)0x80000000;
  uint32_t *query_words = new uint32_t[num_qelts*w2];
  for (int i = 0; i < num_qelts*w2; i++) {
    query_words[i] = ud(generator);
  }
  mpz_t N;
  mpz_init(N);
  mpz_import(N, w, -1, sizeof(uint32_t), 0, 0, N_words);
  cout << "w = " << w << endl;
  //query_t query("test_uuid", w, N, num_qelts);
  query_t query("test_uuid", DEV_W, N, num_qelts);
  mpz_clear(N);
  delete[] N_words;

  mpz_t qelt;
  mpz_init(qelt);
  for (size_t i = 0; i < num_qelts; i++) {
    mpz_import(qelt, w2, -1, sizeof(uint32_t), 0, 0, query_words+i*w2);
    query.set_qelt(i, qelt);
  }
  mpz_clear(qelt);
  delete[] query_words;

  gmp_printf ("N = 0x%Zx\n", query.Nz);
  //for (int i = 0; i < num_qelts; i++) {
  //  std::cout << "query.qelts[" << i << "] = ";
  //  dump(query.qelts.data() + i*w2, w2);
  //}

  size_t *data_rows = new size_t[num_chunks];
  uint32_t *data_chunks = new uint32_t[num_chunks];
  for (size_t i = 0; i < num_chunks; i++) {
    data_rows[i] = i % num_qelts;
    data_chunks[i] = ud2(generator);
  }

  int device_id;
#ifdef USE_GPU
  device_id = 0;
  cudaDeviceProp prop;
  cudaGetDeviceProperties(&prop, device_id);
  cout << "testing with device_id=" << device_id << ", name=" << prop.name << endl;
#else
  device_id = -1;
  cout << "testing with CPU" << endl;
#endif

  cout << "initializing device" << endl;
  device_t dev(device_id, DEV_W, batch_size);
  dev.set_modulus(query);

  cout << "computing answer using Yao" << endl;
  yao_t yao(query, batch_size);
  for (size_t i = 0; i < num_chunks; i++) {
    yao.insert_chunk(data_rows[i], data_chunks[i]);
  }
  mpz_t result;
  mpz_init(result);
  yao.compute(dev, result, 0);
  cout << "done." << endl;

  cout << "computing answer using basic" << endl;
  mpz_t result2;
  mpz_init(result2);
  basic(query, result2, data_rows, data_chunks, num_chunks);
  cout << "done." << endl;

  cout << "checking whether results are the same" << endl;
  assert (!mpz_cmp(result, result2));
  cout << "results are equal" << endl;

  delete[] data_rows;
  delete[] data_chunks;
  //delete[] result;
  mpz_clear(result);
  mpz_clear(result2);
}

int main() {

  cout << "Hello, world!" << endl;

  struct test_t {
    int w;
    int num_qelts;
    int batch_size;
    int max_chunk;
    int num_chunks;
  };
  const struct test_t tests[] = {
/*
    { .w =   1, .num_qelts =  4096, .batch_size = 4, .max_chunk =   255, .num_chunks=  2 },
    { .w =   1, .num_qelts =  4096, .batch_size = 4, .max_chunk =   255, .num_chunks=  1000 },
    { .w =   1, .num_qelts =  4096, .batch_size = 4, .max_chunk =  4095, .num_chunks=  1000 },
    { .w =   1, .num_qelts =  4096, .batch_size = 4, .max_chunk =   255, .num_chunks=  4096 },
    { .w =   1, .num_qelts =  4096, .batch_size = 4, .max_chunk =  4095, .num_chunks=  4096 },
    { .w =   2, .num_qelts =  4096, .batch_size = 4, .max_chunk =   255, .num_chunks=  4096 },
    { .w =  96, .num_qelts =  4096, .batch_size = 4, .max_chunk =   255, .num_chunks=  1000 },
    { .w =  96, .num_qelts =  4096, .batch_size = 4, .max_chunk =   255, .num_chunks=  4096 },
    { .w =  96, .num_qelts = 65536, .batch_size = 4, .max_chunk =   255, .num_chunks=  4096 },
    { .w =  96, .num_qelts = 65536, .batch_size = 4, .max_chunk = 65535, .num_chunks= 65536 },
    { .w =  96, .num_qelts = 65536, .batch_size = 8, .max_chunk = 65535, .num_chunks= 65536 },
    { .w =  96, .num_qelts = 65536, .batch_size = 16, .max_chunk = 65535, .num_chunks= 65536 },
    { .w =  96, .num_qelts = 65536, .batch_size = 32, .max_chunk = 65535, .num_chunks= 65536 },
    { .w =  96, .num_qelts = 65536, .batch_size = 64, .max_chunk = 65535, .num_chunks= 65536 },
    { .w =  96, .num_qelts = 65536, .batch_size = 128, .max_chunk = 65535, .num_chunks= 65536 },
    { .w =  96, .num_qelts = 65536, .batch_size = 256, .max_chunk = 65535, .num_chunks= 65536 },
    { .w =  96, .num_qelts = 65536, .batch_size = 512, .max_chunk = 65535, .num_chunks= 65536 },
    { .w =  96, .num_qelts = 65536, .batch_size = 1024, .max_chunk = 65535, .num_chunks= 65536 },
    { .w =  96, .num_qelts = 65536, .batch_size = 2048, .max_chunk = 65535, .num_chunks= 65536 },
    { .w =  96, .num_qelts = 65536, .batch_size = 4096, .max_chunk = 65535, .num_chunks= 65536 },
*/
    { .w =  96, .num_qelts = 65536, .batch_size = 8192, .max_chunk = 65535, .num_chunks= 65536 },
/*
    { .w =  96, .num_qelts = 65536, .batch_size = 1152, .max_chunk = 65535, .num_chunks= 65536 },
    { .w =  96, .num_qelts = 65536, .batch_size = 2304, .max_chunk = 65535, .num_chunks= 65536 },
    { .w =  96, .num_qelts = 65536, .batch_size = 4608, .max_chunk = 65535, .num_chunks= 65536 },
    { .w =  96, .num_qelts = 65536, .batch_size = 8192, .max_chunk = 65535, .num_chunks= 262144 },
*/
  };
  const int num_tests = sizeof(tests)/sizeof(test_t);

  for (int i = 0; i < num_tests; i++) {
    const test_t t = tests[i];
    do_test(t.w, t.num_qelts, t.batch_size, t.max_chunk, t.num_chunks);
  }

  return 0;
}

