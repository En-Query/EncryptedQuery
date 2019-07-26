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
#include "utils.h"

#include <gmp.h>

#define NDEBUG
#include <cassert>
#include <algorithm>
#include <iomanip>
#include <iostream>
#include <mutex>
#include <stdexcept>
#include <thread>

std::mutex cm;

////////////////////////////////////////////////////////////

bin_t::bin_t(binnum_t binnum) : binnum(binnum) {
}

bin_t::bin_t(binnum_t binnum, const uint32_t *val_ptr) : binnum(binnum) {
  p.push_back(val_ptr);
  r.push_back(0); // dummy value
}

bin_t::bin_t(binnum_t binnum, const uint32_t *val_ptr, size_t row) : binnum(binnum) {
  p.push_back(val_ptr);
  r.push_back(row);
}

////////////////////////////////////////////////////////////

void bins_t::insert_val_ptr(binnum_t binnum, const uint32_t *val_ptr, size_t row) {
  // update max bin number
  if (this->m.size() == 0 || binnum > this->max_binnum) {
    this->max_binnum = binnum;
  }
  auto it = this->m.find(binnum);
  if (it == this->m.end()) {
    this->m[binnum] = this->l.size();
    this->l.push_back(bin_t(binnum, val_ptr, row));
  } else {
    this->l[it->second].p.push_back(val_ptr);
    this->l[it->second].r.push_back(row);
  }
}

void bins_t::clear() {
  this->m.clear();
  this->l.clear();
  this->max_binnum = 0;
  this->binprods.clear();
}

////////////////////////////////////////////////////////////

query_t::query_t(const std::string &uuid, size_t w, mpz_t N, size_t num_qelts)
    : uuid(uuid), ref_count(0), w(w), w2(2*w), num_qelts(num_qelts) {
  assert (mpz_sizeinbase(N,2) <= w*32);
  mpz_init(this->Nz);
  mpz_init(this->N2z);
  mpz_set(this->Nz, N);
  mpz_mul(this->N2z, this->Nz, this->Nz);
  this->qelts = std::vector<uint32_t>(num_qelts*w2);
  this->query_elements = new mpz_t[num_qelts];
  for (size_t i = 0; i < num_qelts; i++) {
    mpz_init(query_elements[i]);
  }

// is the code below needed?
#ifdef USE_RADIXN
  // convert query elements to radix N form
  mpz_t qz;
  mpz_init(qz);
  for (size_t i = 0; i < num_qelts; i++) {
    this->mpzt_from_words(qz, this->qelts.data()+i*w2);
    mpz_fdiv_r(qz, qz, N2z);
    this->words_from_mpzt(this->qelts.data()+i*w2, this->qelts.data()+i*w2+w, qz);
  }
  mpz_clear(qz);
#endif
}

query_t::~query_t() {
  mpz_clear(this->Nz);
  mpz_clear(this->N2z);
  for (size_t i = 0; i < num_qelts; i++) {
    mpz_clear(this->query_elements[i]);
  }
  delete[] this->query_elements;
}

void query_t::set_qelt(size_t row, mpz_t qelt) {
  assert (row < this->num_qelts);
  mpz_set(this->query_elements[row], qelt);
#ifdef USE_RADIXN
  this->words_from_mpzt(this->qelts.data()+row*w2, this->qelts.data()+row*w2+w, qelt);
#else
  this->words_from_mpzt(this->qelts.data()+row*w2, qelt);
#endif
}

void query_t::words_from_mpzt(uint32_t *words, mpz_t z) {
  size_t count;
  assert (mpz_sizeinbase(z,2) <= w2 * 32);
  mpz_export(words, &count, -1, sizeof(uint32_t), 0, 0, z);
  assert (count <= w2);
  if (count < w2) {
    memset(words+count, 0, (w2-count)*sizeof(uint32_t));
  }
}

void query_t::mpzt_from_words(mpz_t z, const uint32_t *words) {
  mpz_import(z, w2, -1, sizeof(uint32_t), 0, 0, words);
}

void query_t::words_from_mpzt(uint32_t *words_lo, uint32_t *words_hi, mpz_t z) {
  size_t count;
  assert (mpz_sizeinbase(z,2) <= w2 * 32);
  mpz_t qz, rz;
  mpz_inits(qz, rz, NULL); 
  mpz_fdiv_qr(qz, rz, z, this->Nz);
  mpz_fdiv_r(qz, qz, this->Nz);  // in case z >= N**2
  mpz_export(words_hi, &count, -1, sizeof(uint32_t), 0, 0, qz);
  assert (count <= w);
  if (count < w) {
    memset(words_hi+count, 0, (w-count)*sizeof(uint32_t));
  }
  mpz_export(words_lo, &count, -1, sizeof(uint32_t), 0, 0, rz);
  assert (count <= w);
  if (count < w) {
    memset(words_lo+count, 0, (w-count)*sizeof(uint32_t));
  }
  mpz_clears(qz, rz, NULL); 
}

void query_t::mpzt_from_words(mpz_t z, const uint32_t *words_lo, const uint32_t *words_hi) {
  mpz_t qz, rz;
  mpz_inits(qz, rz, NULL); 
  mpz_import(qz, w, -1, sizeof(uint32_t), 0, 0, words_hi);
  mpz_import(rz, w, -1, sizeof(uint32_t), 0, 0, words_lo);
  mpz_mul(z, qz, this->Nz);
  mpz_add(z, z, rz);
  mpz_clears(qz, rz, NULL); 
}

////////////////////////////////////////////////////////////

device_t::device_t(int device, size_t w, size_t batch_size)
  : device(device), busy(false), w(w), w2(2*w), batch_size(batch_size) {
  std::thread::id this_id = std::this_thread::get_id();
  //COUT_BEGIN;
  //std::cout << "thread " << this_id << " : ";
  //std::cout << "XXX device_t::device_t(device=" << device << ",w=" << w << ",batch_size=" << batch_size << ")" << std::endl;
  //COUT_END;
  cudaDeviceProp prop;
  if (device >= 0) {
    cudaGetDeviceProperties(&prop, device);
    //std::cout << "device name: " << prop.name << std::endl;
    cudaSetDevice(device);
    bool success = true, succ;
    XMP_CHECK_ERROR(succ, xmpHandleCreate(&handle));
    success &= succ;
    //COUT_BEGIN;
    //std::cout << "thread " << this_id << " : ";
    //std::cout << "XXX new XMP handle = " << handle << std::endl;
    //COUT_END;
#ifdef USE_RADIXN
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&N,           w*32,   1));
    success &= succ;
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&x_lo,        w*32,   batch_size));
    success &= succ;
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&x_hi,        w*32,   batch_size));
    success &= succ;
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&y_lo,        w*32,   batch_size));
    success &= succ;
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&y_hi,        w*32,   batch_size));
    success &= succ;
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&out_lo,      w*32,   batch_size));
    success &= succ;
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&out_hi,      w*32,   batch_size));
    success &= succ;
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&AChi,      2*w*32,   batch_size));
    success &= succ;
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&tmp1,      2*w*32,   batch_size));
    success &= succ;
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&tmp2,      2*w*32,   batch_size));
    success &= succ;
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&tmp3,   2*w*32+32,   batch_size));
    success &= succ;
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&tmp4,   2*w*32+32,   batch_size));
    success &= succ;
#else
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&modulus,    w2*32,   1));
    success &= succ;
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&x,          w2*32,   batch_size));
    success &= succ;
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&y,          w2*32,   batch_size));
    success &= succ;
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&xy,       2*w2*32,   batch_size));
    success &= succ;
    XMP_CHECK_ERROR(succ, xmpIntegersCreate(handle,&out,        w2*32,   batch_size));
    success &= succ;
#endif
    if (!success) {
	throw std::runtime_error("failed to initialize XMP objects");
    }
  } else {
      //std::cout << "negative device id -- will use CPU" << std::endl;
  }
}

device_t::~device_t() {
    std::thread::id this_id = std::this_thread::get_id();
    //COUT_BEGIN;
    //std::cout << "thread " << this_id << " : ";
    //std::cout << "XXX device_t::~device_t()" << std::endl;
    //COUT_END;
    if (device >= 0) {
	//COUT_BEGIN;
	//std::cout << "thread " << this_id << " : ";
	//std::cout << "XXX deleting XMP handle " << handle << std::endl;
	//COUT_END;
	bool success = true;
#ifdef USE_RADIXN
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,N));
	if (!success) return;
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,x_lo));
	if (!success) return;
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,x_hi));
	if (!success) return;
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,y_lo));
	if (!success) return;
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,y_hi));
	if (!success) return;
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,out_lo));
	if (!success) return;
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,out_hi));
	if (!success) return;
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,AChi));
	if (!success) return;
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,tmp1));
	if (!success) return;
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,tmp2));
	if (!success) return;
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,tmp3));
	if (!success) return;
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,tmp4));
	if (!success) return;
#else
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,modulus));
	if (!success) return;
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,x));
	if (!success) return;
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,y));
	if (!success) return;
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,xy));
	if (!success) return;
	XMP_CHECK_ERROR(success, xmpIntegersDestroy(handle,out));
	if (!success) return;
#endif
	XMP_CHECK_ERROR(success, xmpHandleDestroy(handle));
	if (!success) return;
    }
}

void device_t::set_modulus(query_t &query) {
  assert (query.w == w);
  assert (query.w2 == w2);
  if (device >= 0) {
    // initialize XMP integer with modulus
#ifdef USE_RADIXN
    uint32_t *N_words = new uint32_t[w];
    size_t count;
    mpz_export(N_words, &count, -1, sizeof(uint32_t), 0, 0, query.Nz);
    assert (count <= w);
    if (count < w) {
      memset(N_words + count, 0, (w-count)*sizeof(uint32_t));
    }
    bool succ;
    XMP_CHECK_ERROR(succ, xmpIntegersImport(handle,this->N,w,-1,sizeof(uint32_t),0,0,N_words,1));
    assert (succ);
    delete[] N_words;
#else
    uint32_t *modulus_words = new uint32_t[w2];
    query.words_from_mpzt(modulus_words, query.N2z);
    bool succ;
    XMP_CHECK_ERROR(succ, xmpIntegersImport(handle,this->modulus,w2,-1,sizeof(uint32_t),0,0,modulus_words,1));
    assert (succ);
    delete[] modulus_words;
#endif
  }
}

#ifdef USE_RADIXN

void device_t::batch_modmul_radixn(query_t &query, uint32_t *oo_lo, uint32_t *oo_hi, uint32_t *xx_lo, uint32_t *xx_hi, uint32_t *yy_lo, uint32_t *yy_hi, size_t num) {
  uint64_t modmul_start = time_now();
  const size_t B = batch_size;
  mpz_t x_loz, x_hiz, y_loz, y_hiz, o_loz, o_hiz;
  mpz_t tmp1z, tmp2z, tmp3z, tmp4z, AChiz;
  const bool debug_check = false;
  if (device < 0 || debug_check) {
    mpz_inits(x_loz, x_hiz, y_loz, y_hiz, o_loz, o_hiz, NULL);
    mpz_inits(tmp1z, tmp2z, tmp3z, tmp4z, AChiz, NULL);
  }
  for (size_t start = 0; start < num; start += B) {
    size_t howmany = num - start;
    if (howmany > B) {
      howmany = B;
    }
    if (device >= 0) {
	//COUT_BEGIN;
	//std::thread::id this_id = std::this_thread::get_id();
	//std::cout << "thread " << this_id << " : ";
	//std::cout << "using GPU" << std::endl;
	//COUT_END;
	bool success = true, succ;
	XMP_CHECK_ERROR(succ, xmpIntegersImport(handle,x_lo,w,-1,sizeof(uint32_t),0,0,xx_lo+start*w,howmany));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersImport(handle,x_hi,w,-1,sizeof(uint32_t),0,0,xx_hi+start*w,howmany));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersImport(handle,y_lo,w,-1,sizeof(uint32_t),0,0,yy_lo+start*w,howmany));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersImport(handle,y_hi,w,-1,sizeof(uint32_t),0,0,yy_hi+start*w,howmany));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersMul(handle, tmp1, x_lo, y_lo, howmany));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersDivMod(handle, AChi, out_lo, tmp1, N, howmany));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersMul(handle, tmp1, x_lo, y_hi, howmany));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersMul(handle, tmp2, x_hi, y_lo, howmany));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersAdd(handle, tmp3, tmp1, tmp2, howmany));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersAdd(handle, tmp4, tmp3, AChi, howmany));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersMod(handle, out_hi, tmp4, N, howmany));
	success &= succ;
	uint32_t words;
	XMP_CHECK_ERROR(succ, xmpIntegersExport(handle, oo_lo+start*w, &words, -1, sizeof(uint32_t), 0, 0, out_lo, howmany));
	success &= succ;
	success &= (words == (uint32_t)w);
	XMP_CHECK_ERROR(succ, xmpIntegersExport(handle, oo_hi+start*w, &words, -1, sizeof(uint32_t), 0, 0, out_hi, howmany));
	success &= succ;
	success &= (words == (uint32_t)w);
	if (!success) {
	    throw std::runtime_error("XMP error occurred");
	}

	if (debug_check) {
	    std::thread::id this_id = std::this_thread::get_id();
	    std::vector<uint32_t> oo_lo_tmp(howmany*w);
	    std::vector<uint32_t> oo_hi_tmp(howmany*w);

	    for (size_t i = 0; i < howmany; i++) {
		mpz_import(x_loz, w, -1, sizeof(uint32_t), 0, 0, xx_lo+(start+i)*w);
		mpz_import(x_hiz, w, -1, sizeof(uint32_t), 0, 0, xx_hi+(start+i)*w);
		mpz_import(y_loz, w, -1, sizeof(uint32_t), 0, 0, yy_lo+(start+i)*w);
		mpz_import(y_hiz, w, -1, sizeof(uint32_t), 0, 0, yy_hi+(start+i)*w);
		mpz_mul(tmp1z, x_loz, y_loz);
		mpz_fdiv_qr(AChiz, o_loz, tmp1z, query.Nz);
		mpz_mul(tmp1z, x_loz, y_hiz);
		mpz_mul(tmp2z, x_hiz, y_loz);
		mpz_add(tmp3z, tmp1z, tmp2z);
		mpz_add(tmp4z, tmp3z, AChiz);
		mpz_fdiv_r(o_hiz, tmp4z, query.Nz);
		size_t count;
		mpz_export(oo_lo_tmp.data()+i*w, &count, -1, sizeof(uint32_t), 0, 0, o_loz);
		assert (count <= w);
		if (count < w) {
		    memset(oo_lo_tmp.data()+i*w+count, 0, (w-count)*sizeof(uint32_t));
		}
		mpz_export(oo_hi_tmp.data()+i*w, &count, -1, sizeof(uint32_t), 0, 0, o_hiz);
		assert (count <= w);
		if (count < w) {
		    memset(oo_hi_tmp.data()+i*w+count, 0, (w-count)*sizeof(uint32_t));
		}
	    }

	    if (!memcmp(oo_lo+start*w, oo_lo_tmp.data(), howmany*w)
		&& !memcmp(oo_hi+start*w, oo_hi_tmp.data(), howmany*w)) {
		COUT_BEGIN;
		std::cout << "thread " << this_id << " : XXX internal check passes" << std::endl;
		COUT_END;
	    } else {
		COUT_BEGIN;
		std::cout << "thread " << this_id << " : XXX interal check FAILED" << std::endl;
		COUT_END;
	    }

	}

    } else {
      // compute modmuls using radix-N
      for (size_t i = 0; i < howmany; i++) {
	mpz_import(x_loz, w, -1, sizeof(uint32_t), 0, 0, xx_lo+i*w);
	mpz_import(x_hiz, w, -1, sizeof(uint32_t), 0, 0, xx_hi+i*w);
	mpz_import(y_loz, w, -1, sizeof(uint32_t), 0, 0, yy_lo+i*w);
	mpz_import(y_hiz, w, -1, sizeof(uint32_t), 0, 0, yy_hi+i*w);
	mpz_mul(tmp1z, x_loz, y_loz);
	mpz_fdiv_qr(AChiz, o_loz, tmp1z, query.Nz);
	mpz_mul(tmp1z, x_loz, y_hiz);
	mpz_mul(tmp2z, x_hiz, y_loz);
	mpz_add(tmp3z, tmp1z, tmp2z);
	mpz_add(tmp4z, tmp3z, AChiz);
	mpz_fdiv_r(o_hiz, tmp4z, query.Nz);
	size_t count;
	mpz_export(oo_lo+i*w, &count, -1, sizeof(uint32_t), 0, 0, o_loz);
	assert (count <= w);
	if (count < w) {
	  memset(oo_lo+i*w+count, 0, (w-count)*sizeof(uint32_t));
	}
	mpz_export(oo_hi+i*w, &count, -1, sizeof(uint32_t), 0, 0, o_hiz);
	assert (count <= w);
	if (count < w) {
	memset(oo_hi+i*w+count, 0, (w-count)*sizeof(uint32_t));
	}
      }
    }
    uint64_t modmul_time = time_now() - modmul_start;
#ifdef GPURESPONDER_DEBUG
    COUT_BEGIN;
    std::cout << __func__ << ": modmul_time=" << modmul_time;
    std::cout << ", muls/s=" << 1.0e9 * howmany / modmul_time;
    std::cout << ", #muls=" << howmany << std::endl;
    COUT_END;
#endif
  }
  if (device < 0 || debug_check) {
    mpz_clears(x_loz, x_hiz, y_loz, y_hiz, o_loz, o_hiz, NULL);
    mpz_clears(tmp1z, tmp2z, tmp3z, tmp4z, AChiz, NULL);
  }
}

#else // not defined(USE_RADIXN)

void device_t::batch_modmul(query_t &query, uint32_t *oo, uint32_t *xx, uint32_t *yy, size_t num) {
  uint64_t modmul_start = time_now();
  const size_t B = batch_size;
  mpz_t xz, yz, o;
  if (device < 0) {
    mpz_inits(xz, yz, o, NULL);
  }
  for (size_t start = 0; start < num; start += B) {
    size_t howmany = num - start;
    if (howmany > B) {
      howmany = B;
    }
    if (device >= 0) {
	bool success = true, succ;
	XMP_CHECK_ERROR(succ, xmpIntegersImport(handle,x,w2,-1,sizeof(uint32_t),0,0,xx+start*w2,howmany));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersImport(handle,y,w2,-1,sizeof(uint32_t),0,0,yy+start*w2,howmany));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersMulAsync(handle, xy, x, y, howmany));
	success &= succ;
	XMP_CHECK_ERROR(succ, xmpIntegersMod(handle, out, xy, modulus, howmany));
	success &= succ;
	uint32_t words;
	XMP_CHECK_ERROR(succ, xmpIntegersExport(handle, oo+start*w2, &words, -1, sizeof(uint32_t), 0, 0, out, howmany));
	success &= succ;
	success &= (words == (uint32_t)w2);
	if (!success) {
	    throw runtime_error("XMP error occurred");
	}
    } else {
      for (size_t i = 0; i < howmany; i++) {
	query.mpzt_from_words(xz, xx + i*w2);
	query.mpzt_from_words(yz, yy + i*w2);
	mpz_mul(o, xz, yz);
	mpz_fdiv_r(o, o, query.N2z);
	query.words_from_mpzt(oo + start*w2 + i*w2, o);
      }
    }
    uint64_t modmul_time = time_now() - modmul_start;
#ifdef GPURESPONDER_DEBUG
    COUT_BEGIN;
    std::cout << __func__ << ": modmul_time=" << modmul_time;
    std::cout << ", muls/s=" << 1.0e9 * howmany / modmul_time;
    std::cout << ", #muls=" << howmany << std::endl;
    COUT_END;
#endif
  }
  if (device < 0) {
    mpz_clears(xz, yz, o, NULL);
  }
}

#endif // USE_RADIXN

////////////////////////////////////////////////////////////

yao_t::yao_t(query_t &query, size_t batch_size) : query(query), batch_size(batch_size), weight(0) {
}

void yao_t::insert_chunk(size_t row, binnum_t chunk) {
    if (chunk != 0) {
	bins.insert_val_ptr(chunk, query.qelts.data()+row*query.w2, row);
    }
    weight++;
}

void yao_t::insert_chunk_bi(uint32_t *v, binnum_t chunk) {
    if (chunk != 0) {
	bins.insert_val_ptr(chunk, v, 0);//dummy row number
    }
    weight++;
}

void yao_t::compute(device_t &dev, mpz_t result, int depth=0) {
  uint64_t compute_start = time_now();
  compute_binprods(dev, query, batch_size, bins, depth+1);
  combine_binprods(dev, query, batch_size, result, bins, depth+1);
  uint64_t compute_time = time_now() - compute_start;
#ifdef GPURESPONDER_DEBUG
  COUT_BEGIN;
  for (int i = 0; i < depth; i++) {
    std::cout << "    ";
  }
  std::cout << "compute_time:  " << std::setw(12) << compute_time << std::endl;
  COUT_END;
#endif
}

void yao_t::clear() {
  this->bins.clear();
  this->weight = 0;
}

void yao_t::dump() {
    std::thread::id this_id = std::this_thread::get_id();
    COUT_BEGIN;
    std::cout << "thread " << this_id << " : ";
    std::cout << "bin contents:" << std::endl;
    for (auto it = bins.m.begin(); it != bins.m.end(); it++) {
	binnum_t binnum = it->first;
	std::cout << "thread " << this_id << " : ";
	std::cout << "  binnum = " << binnum << " : ";
	bin_t &bin = bins.l[it->second];
	for (auto it2 = bin.p.begin(); it2 != bin.p.end(); it2++) {
	    const uint32_t *ptr = *it2;
	    size_t offset = (ptr - query.qelts.data()) / query.w2;
	    std::cout << offset << ", ";
	}
	std::cout << std::endl;
    }
    COUT_END;
}

////////////////////////////////////////////////////////////

void compute_binprods(device_t &dev, query_t &query, size_t batch_size, bins_t &bins, int depth=0) {
  uint64_t binprods_start = time_now();
  uint64_t batchmul_time = 0;
  uint64_t memcpy_time = 0;

  uint64_t init_start = time_now();
  std::vector<bin_t> &bl = bins.l;
  std::list<size_t> index;
  for (size_t i = 0; i < bl.size(); i++) {
    index.push_back(i);
  }

  const size_t w = query.w;
  const size_t w2 = query.w2;
  const size_t B = batch_size;
  uint32_t *xx = new uint32_t[B * w2];
  uint32_t *yy = new uint32_t[B * w2];
  uint32_t *oo = new uint32_t[B * w2];
  uint32_t *oo_end = oo + B*w2;

#ifdef USE_RADIXN
  size_t off = B * w;
  uint32_t *xx_lo = xx;
  uint32_t *xx_hi = xx + off;
  uint32_t *yy_lo = yy;
  uint32_t *yy_hi = yy + off;
  uint32_t *oo_lo = oo;
  uint32_t *oo_hi = oo + off;
#endif
  
  mpz_t m;
  mpz_init(m);
  mpz_set(m, query.N2z);

  bins.binprods.resize(bl.size() * w2);
  uint32_t *binprods = bins.binprods.data();

  uint64_t init_time = time_now() - init_start;

  size_t total_muls = 0;
  while (true) {
    if (index.size() == 0) {
      break;
    }

    size_t used = 0;
    for (auto it=index.begin(); it!=index.end(); ) {

      if (used >= B) {
	break;
      }

      bin_t &bin = bl[*it];
      binnum_t chunk = bin.binnum;
      std::vector<const uint32_t*> &rows = bin.p;
      assert (rows.size() >= 1);

      bool in_oo = oo <= rows[0] && rows[0] < oo_end;

      // If the bin size (i.e. rows.size()) is odd, we may need to
      // save off the first value (pointed to by rows[0]).  This will
      // be the case if rows[0] points into the output buffer, because
      // that value may be clobbered soon.  We will also need to copy
      // the value regardless of where rows[0] points to, if
      // rows.size() == 1, because we are done with this bin.
      if (rows.size() % 2 == 1 && (in_oo || rows.size() == 1)) {
#ifdef USE_RADIXN
	size_t offset = in_oo ? off : w;
	memcpy(binprods+(*it)*w2, rows[0], w*sizeof(uint32_t));
	memcpy(binprods+(*it)*w2+w, rows[0]+offset, w*sizeof(uint32_t));
#else
	memcpy(binprods+(*it)*w2, rows[0], w2*sizeof(uint32_t));
#endif
	rows[0] = binprods+(*it)*w2;
      }
      
      // If bin.size() == 1, the bin is done.
      if (rows.size() == 1) {
	rows.pop_back();
	it = index.erase(it);
	continue;
      }

      // Schedule work from current bin
      size_t muls = rows.size() / 2;
      if (muls > B - used) {
	muls = B - used;
      }
      //bool temp_used = (muls >= 1 && rows.size() == 2*muls && rows[0] == bin.local);
      for (size_t j = 0; j < muls; j++) {
	const uint32_t *r0 = rows.back();
	rows.pop_back();
	const uint32_t *r1 = rows.back();
	rows.pop_back();
	uint64_t memcpy_start = time_now();
#ifdef USE_RADIXN
	bool in_oo = oo <= r0 && r0 < oo_end;
	size_t offset = in_oo ? off : w;
	memcpy(xx_lo + (used + j)*w, r0, w*sizeof(uint32_t));
	memcpy(xx_hi + (used + j)*w, r0+offset, w*sizeof(uint32_t));
	in_oo = oo <= r1 && r1 < oo_end;
	offset = in_oo ? off : w;
	memcpy(yy_lo + (used + j)*w, r1, w*sizeof(uint32_t));
	memcpy(yy_hi + (used + j)*w, r1+offset, w*sizeof(uint32_t));
#else
	memcpy(xx + (used + j)*w2, r0, w2*sizeof(uint32_t));
	memcpy(yy + (used + j)*w2, r1, w2*sizeof(uint32_t));
#endif
	memcpy_time += time_now() - memcpy_start;
      }
      for (size_t j = 0; j < muls; j++) {
#ifdef USE_RADIXN
	rows.push_back(oo_lo + (used + j)*w);
#else
	rows.push_back(oo + (used + j)*w2);
#endif
      }
      used += muls;

      // Update iterator for list of indices to remaining bins
      it++;
    }

    //// do work
    //std::cout << "-------------------- doing work --------------------" << std:: endl;
    //std::cout << "used = " << used << std::endl;
    if (used > 0) {
      total_muls += used;
      uint64_t batchmul_start = time_now();
#ifdef USE_RADIXN
      dev.batch_modmul_radixn(query, oo_lo, oo_hi, xx_lo, xx_hi, yy_lo, yy_hi, used);
#else
      dev.batch_modmul(query, oo, xx, yy, used);
#endif
      batchmul_time += time_now() - batchmul_start;
    }
  }

  delete[] xx;
  delete[] yy;
  delete[] oo;
  mpz_clear(m);

#ifdef GPURESPONDER_DEBUG
  COUT_BEGIN;
  uint64_t binprods_time = time_now() - binprods_start;
  for (int i = 0; i < depth; i++) {
    std::cout << "    ";
  }
  std::cout << "init_time:     " << std::setw(12) << init_time << std::endl;
  for (int i = 0; i < depth; i++) {
    std::cout << "    ";
  }
  std::cout << "memcpy_time:   " << std::setw(12) << memcpy_time << std::endl;
  for (int i = 0; i < depth; i++) {
    std::cout << "    ";
  }
  std::cout << "batchmul_time: " << std::setw(12) << batchmul_time;
  std::cout << " (muls/s=" << 1.0e9 * total_muls/batchmul_time << ", #muls=" << total_muls << ")";
  std::cout << std::endl;
  for (int i = 0; i < depth; i++) {
    std::cout << "    ";
  }
  std::cout << "binprods_time: " << std::setw(12) << binprods_time << std::endl;
  COUT_END;
#endif
}

static int size_in_bits(binnum_t x) {
  int ans = 0;
  while (x != 0) {
    ans++;
    x >>= 1;
  }
  return ans;
}

void combine_binprods(device_t &dev, query_t &query, size_t batch_size, mpz_t result, bins_t &bins, int depth=0) {
  const int recur_thresh = 8;
  const size_t w = query.w;
  const size_t w2 = query.w2;

  if (bins.l.size() == 0) {
    mpz_set_ui(result, 1);
    return;
  }
  uint32_t *binprods = bins.binprods.data();

  uint64_t combine_start = time_now();

  mpz_t outz;
  mpz_init(outz);

  binnum_t maxbin = bins.max_binnum;
  int maxbin_bitlen = size_in_bits(maxbin);
  if (maxbin_bitlen > recur_thresh) {
    // recursive case

    int numlowbits = maxbin_bitlen / 2;
    binnum_t mask = ((binnum_t)1 << numlowbits) - 1;
    yao_t cy(query, batch_size);
    mpz_t low, high;
    mpz_inits(low, high, NULL);
#ifdef GPURESPONDER_DEBUG
    COUT_BEGIN;
    std::cout << "recursively computing low" << std::endl;
    COUT_END;
#endif
    for (size_t i = 0; i < bins.l.size(); i++) {
      bin_t &bin = bins.l[i];
      binnum_t chunk = bin.binnum & mask;
      uint32_t *v = binprods + i*w2;
      cy.insert_chunk_bi(v, chunk);
    }
    cy.compute(dev, low, depth+1);
    yao_t cy2(query, batch_size);
#ifdef GPURESPONDER_DEBUG
    COUT_BEGIN;
    std::cout << "recursively computing high" << std::endl;
    COUT_END;
#endif
    for (size_t i = 0; i < bins.l.size(); i++) {
      bin_t &bin = bins.l[i];
      binnum_t chunk = bin.binnum >> numlowbits;
      uint32_t *v = binprods + i*w2;
      cy2.insert_chunk_bi(v, chunk);
    }
    cy2.compute(dev, high, depth+1);
    mpz_powm_ui(outz, high, 1<<numlowbits, query.N2z);
    mpz_mul(outz, outz, low);
    mpz_fdiv_r(outz, outz, query.N2z);
    mpz_clears(low, high, NULL);

  } else {
    // base case
    size_t total_muls = 0;
    uint64_t basecase_start = time_now();

    mpz_t x, y, v;
    mpz_inits(x, y, v, NULL);
    mpz_set_ui(x, 1);
    bool first = true;
    for (binnum_t binnum=bins.max_binnum; binnum > 0; binnum--) {
      auto it = bins.m.find(binnum);
      if (it == bins.m.end()) {
	mpz_set_ui(v, 1);
      } else {
#ifdef USE_RADIXN
	query.mpzt_from_words(v, binprods+it->second*w2, binprods+it->second*w2+w);
#else
	query.mpzt_from_words(v, binprods+it->second*w2);
#endif
      }
      if (first) {
	mpz_set(x, v);
	mpz_set(y, v);
	first = false;
      } else {
	mpz_mul(y, y, v);
	mpz_fdiv_r(y, y, query.N2z);
	mpz_mul(x, x, y);
	mpz_fdiv_r(x, x, query.N2z);
	total_muls += 2;
      }
    }
    mpz_set(outz, x);
    mpz_clears(x, y, v, NULL);

#ifdef GPURESPONDER_DEBUG
    uint64_t basecase_time = time_now() - basecase_start;
    COUT_BEGIN;
    for (int i = 0; i < depth; i++) {
      std::cout << "    ";
    }
    std::cout << "basecase_time: " << std::setw(12) << basecase_time;
    std::cout << " (muls/s=" << 1.0e9 * total_muls / basecase_time << ", #muls=" << total_muls << ")";
    std::cout << std::endl;
    COUT_END;
#endif
  }

 done:
  // write out answer
  mpz_set(result, outz);
  mpz_clear(outz);

#ifdef GPURESPONDER_DEBUG
  uint64_t combine_time = time_now() - combine_start;
  COUT_BEGIN;
  for (int i = 0; i < depth; i++) {
    std::cout << "    ";
  }
  std::cout << "combine_time:  " << std::setw(12) << combine_time << std::endl;
  COUT_END;
#endif
}

