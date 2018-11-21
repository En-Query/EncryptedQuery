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

#include "basic.h"
#include "derooij.h"
#include "derooij_wrap.h"
#include "yao.h"
#include "yao_wrap.h"

#include <gmp.h>

#include <assert.h>
#include <inttypes.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define CHECK_BASIC

void gen_rand_modulus(gmp_randstate_t state, mpz_t modulus, int bits) {
  mpz_init2(modulus, bits);
  mpz_urandomb(modulus, state, bits);
  mpz_setbit(modulus, 0);
  mpz_setbit(modulus, bits-1);
}

typedef struct exp_col_problem_t {
  int bits;
  int b;
  int p;
  mpz_t modulus;
  mpz_t* gs;
  mpz_t* xs;
} exp_col_problem_t;

void exp_col_problem_init(exp_col_problem_t *problem, mpz_t modulus, int bits, int b, int p) {
  problem->bits = bits;
  problem->b = b;
  problem->p = p;
  int i;
  mpz_init_set(problem->modulus, modulus);
  problem->gs = (mpz_t*)calloc(p, sizeof(mpz_t));
  for (i=0; i<problem->p; i++) {
    mpz_init2(problem->gs[i], bits);
  }
  problem->xs = (mpz_t*)calloc(p, sizeof(mpz_t));
  for (i=0; i<problem->p; i++) {
    mpz_init2(problem->xs[i], bits);
  }
}

void exp_col_problem_fini(exp_col_problem_t *problem) {
  int i;
  mpz_clear(problem->modulus);
  for (i=0; i<problem->p; i++) {
    mpz_clear(problem->gs[i]);
  }
  free(problem->gs);
  for (i=0; i<problem->p; i++) {
    mpz_clear(problem->xs[i]);
  }
  free(problem->xs);
  memset(problem, 0, sizeof(exp_col_problem_t));
}

void new_problem(gmp_randstate_t state, exp_col_problem_t *problem) {
  int i;
  for (i=0; i<problem->p; i++) {
    mpz_urandomb(problem->gs[i], state, problem->bits);
  }
  for (i=0; i<problem->p; i++) {
    mpz_urandomb(problem->xs[i], state, problem->b);
  }
}

void exp_col_basic(basic_ctx_t *basic, mpz_t out, exp_col_problem_t *problem) {
  int i;
  for (i=0; i<problem->p; i++) {
    int part = (int) mpz_get_si(problem->xs[i]);
    basic_insert_data_part2(basic, problem->gs[i], part);
  }
  basic_compute_column_and_clear_data(basic, out);
}

void exp_col_derooij(derooij_ctx_t *derooij, mpz_t out, exp_col_problem_t *problem) {
  int i;
  for (i=0; i<problem->p; i++) {
    int part = (int) mpz_get_si(problem->xs[i]);
    derooij_insert_data_part2(derooij, problem->gs[i], part);
  }
  derooij_compute_column_and_clear_data(derooij, out);
}

void exp_col_yao(yao_ctx_t *yao, mpz_t out, exp_col_problem_t *problem) {
  int i;
  for (i=0; i<problem->p; i++) {
    int part = (int) mpz_get_si(problem->xs[i]);
    yao_insert_data_part2(yao, problem->gs[i], part);
  }
  yao_compute_column_and_clear_data(yao, out);
}

void exp_col_derooij_wrap(derooij_wrap_t *wrap, mpz_t out, exp_col_problem_t *problem) {
  int i;
  for (i=0; i<problem->p; i++) {
    int part = (int) mpz_get_si(problem->xs[i]);
    derooij_wrap_insert_data_part(wrap, i, part);
  }
  derooij_compute_column_and_clear_data(&wrap->derooij_ctx, out);
}

void exp_col_yao_wrap(yao_wrap_t *wrap, mpz_t out, exp_col_problem_t *problem) {
  int i;
  for (i=0; i<problem->p; i++) {
    int part = (int) mpz_get_si(problem->xs[i]);
    yao_wrap_insert_data_part(wrap, i, part);
  }
  yao_compute_column_and_clear_data(&wrap->yao_ctx, out);
}

uint64_t time_now() {
  struct timespec tp;
  clock_gettime(CLOCK_PROCESS_CPUTIME_ID, &tp);
  return (uint64_t) tp.tv_sec * 1000000000u + tp.tv_nsec;
}

int test_derooij(int bits, int b, int p, int T) {
  gmp_randstate_t state;

  exp_col_problem_t problem;
  int i;
  int t;
  derooij_ctx_t derooij;
  basic_ctx_t basic;
  mpz_t modulus, out, out2;
  uint64_t start, derooij_elapsed, basic_elapsed;
  uint64_t derooij_total, basic_total;
  double derooij_rate, basic_rate;
  int status = 0;

  printf ("    testing derooij (bits=%d, b=%d, p=%d, T=%d)\n", bits, b, p, T);

  mpz_inits(modulus, out, out2, NULL);
  gmp_randinit_default(state);
  gen_rand_modulus(state, modulus, bits);
  exp_col_problem_init(&problem, modulus, bits, b, p);

  basic_init(&basic, modulus);

  derooij_total = 0;
  basic_total = 0;
  for (t = 0; t < T; t++) {
    new_problem(state, &problem);

    derooij_init(&derooij, modulus, p);
    start = time_now();
    exp_col_derooij(&derooij, out, &problem);
    derooij_elapsed = time_now() - start;

#ifdef CHECK_BASIC
    start = time_now();
    exp_col_basic(&basic, out2, &problem);
    basic_elapsed = time_now() - start;
    if (0 != mpz_cmp(out, out2)) {
      printf ("    answer mismatch!\n");
      status = 1;
    }
    basic_total += basic_elapsed;
#endif

    derooij_total += derooij_elapsed;
    derooij_rate = 1.0 * 1000000000 * p * b/8 / (1.0 * derooij_elapsed);

    derooij_fini(&derooij);
    if (status > 0) {
      goto cleanup;
    }
  }

  derooij_rate = 1.0 * 1000000000 * p * b/8 / (1.0 * derooij_total / T);
  basic_rate = 1.0 * 1000000000 * p * b/8 / (1.0 * basic_total / T);

  printf ("    derooij rate:  %0.2f bytes/sec\n", derooij_rate);
  printf ("    basic rate:  %0.2f bytes/sec\n", basic_rate);

cleanup:
  mpz_clears(modulus, out, out2, NULL);
  exp_col_problem_fini(&problem);
  basic_fini(&basic);

  return status;
}

int test_yao(int bits, int b, int p, int T) {
  gmp_randstate_t state;

  exp_col_problem_t problem;
  int i;
  int t;
  yao_ctx_t yao;
  basic_ctx_t basic;
  mpz_t modulus, out, out2;
  uint64_t start, yao_elapsed, basic_elapsed;
  uint64_t yao_total, basic_total;
  double yao_rate, basic_rate;
  int status = 0;

  printf ("    testing yao (bits=%d, b=%d, p=%d, T=%d)\n", bits, b, p, T);

  mpz_inits(modulus, out, out2, NULL);
  gmp_randinit_default(state);
  gen_rand_modulus(state, modulus, bits);
  exp_col_problem_init(&problem, modulus, bits, b, p);

  basic_init(&basic, modulus);

  yao_total = 0;
  basic_total = 0;
  for (t = 0; t < T; t++) {
    new_problem(state, &problem);

    yao_init(&yao, modulus, b);
    start = time_now();
    exp_col_yao(&yao, out, &problem);
    yao_elapsed = time_now() - start;

#ifdef CHECK_BASIC
    start = time_now();
    exp_col_basic(&basic, out2, &problem);
    basic_elapsed = time_now() - start;
    if (0 != mpz_cmp(out, out2)) {
      printf ("    answer mismatch!\n");
      status = 1;
    }
    basic_total += basic_elapsed;
#endif

    yao_total += yao_elapsed;
    yao_rate = 1.0 * 1000000000 * p * b/8 / (1.0 * yao_elapsed);

    yao_fini(&yao);
    if (status > 0) {
      goto cleanup;
    }
  }

  yao_rate = 1.0 * 1000000000 * p * b/8 / (1.0 * yao_total / T);
  basic_rate = 1.0 * 1000000000 * p * b/8 / (1.0 * basic_total / T);

  printf ("    yao_rate:  %0.2f bytes/sec\n", yao_rate);
  printf ("    basic rate:  %0.2f bytes/sec\n", basic_rate);

cleanup:
  mpz_clears(modulus, out, out2, NULL);
  exp_col_problem_fini(&problem);
  basic_fini(&basic);

  return status;
}

int test_derooij_wrap(int bits, int b, int p, int T) {
  gmp_randstate_t state;

  exp_col_problem_t problem;
  int i;
  int t;
  derooij_wrap_t wrap;
  basic_ctx_t basic;
  mpz_t modulus, out, out2;
  uint64_t start, derooij_elapsed, basic_elapsed;
  uint64_t derooij_total, basic_total;
  double derooij_rate, basic_rate;
  int status = 0;

  printf ("    testing derooij_wrap (bits=%d, b=%d, p=%d, T=%d)\n", bits, b, p, T);

  mpz_inits(modulus, out, out2, NULL);
  gmp_randinit_default(state);
  gen_rand_modulus(state, modulus, bits);
  exp_col_problem_init(&problem, modulus, bits, b, p);

  basic_init(&basic, modulus);

  derooij_total = 0;
  basic_total = 0;
  for (t = 0; t < T; t++) {
    new_problem(state, &problem);

    derooij_wrap_init(&wrap, modulus, p);
    for (i=0; i<p; i++) {
      mpz_set(wrap.query_elements[i], problem.gs[i]);
    }
  
    start = time_now();
    exp_col_derooij_wrap(&wrap, out, &problem);
    derooij_elapsed = time_now() - start;

#ifdef CHECK_BASIC
    start = time_now();
    exp_col_basic(&basic, out2, &problem);
    basic_elapsed = time_now() - start;
    if (0 != mpz_cmp(out, out2)) {
      printf ("    answer mismatch!\n");
      status = 1;
    }
    basic_total += basic_elapsed;
#endif
    
    derooij_total += derooij_elapsed;
    derooij_rate = 1.0 * 1000000000 * p * b/8 / (1.0 * derooij_elapsed);

    derooij_wrap_fini(&wrap);
    if (status > 0) {
      goto cleanup;
    }
  }

  derooij_rate = 1.0 * 1000000000 * p * b/8 / (1.0 * derooij_total / T);
  basic_rate = 1.0 * 1000000000 * p * b/8 / (1.0 * basic_total / T);

  printf ("    derooij rate:  %0.2f bytes/sec\n", derooij_rate);
  printf ("    basic rate:  %0.2f bytes/sec\n", basic_rate);

cleanup:
  mpz_clears(modulus, out, out2, NULL);
  exp_col_problem_fini(&problem);
  basic_fini(&basic);

  return status;
}

int test_yao_wrap(int bits, int b, int p, int T) {
  gmp_randstate_t state;

  exp_col_problem_t problem;
  int i;
  int t;
  yao_wrap_t wrap;
  basic_ctx_t basic;
  mpz_t modulus, out, out2;
  uint64_t start, yao_elapsed, basic_elapsed;
  uint64_t yao_total, basic_total;
  double yao_rate, basic_rate;
  int status = 0;

  printf ("    testing yao_wrap (bits=%d, b=%d, p=%d, T=%d)\n", bits, b, p, T);

  mpz_inits(modulus, out, out2, NULL);
  gmp_randinit_default(state);
  gen_rand_modulus(state, modulus, bits);
  exp_col_problem_init(&problem, modulus, bits, b, p);

  basic_init(&basic, modulus);

  yao_total = 0;
  basic_total = 0;
  for (t = 0; t < T; t++) {
    new_problem(state, &problem);

    yao_wrap_init(&wrap, modulus, p, b);
    for (i=0; i<p; i++) {
      mpz_set(wrap.query_elements[i], problem.gs[i]);
    }

    start = time_now();
    exp_col_yao_wrap(&wrap, out, &problem);
    yao_elapsed = time_now() - start;

#ifdef CHECK_BASIC
    start = time_now();
    exp_col_basic(&basic, out2, &problem);
    basic_elapsed = time_now() - start;
    if (0 != mpz_cmp(out, out2)) {
      printf ("    answer mismatch!\n");
      status = 1;
    }
    basic_total += basic_elapsed;
#endif

    yao_total += yao_elapsed;
    yao_rate = 1.0 * 1000000000 * p * b/8 / (1.0 * yao_elapsed);

    yao_wrap_fini(&wrap);
    if (status > 0) {
      goto cleanup;
    }
  }

  yao_rate = 1.0 * 1000000000 * p * b/8 / (1.0 * yao_total / T);
  basic_rate = 1.0 * 1000000000 * p * b/8 / (1.0 * basic_total / T);

  printf ("    yao rate:  %0.2f bytes/sec\n", yao_rate);
  printf ("    basic rate:  %0.2f bytes/sec\n", basic_rate);

cleanup:
  mpz_clears(modulus, out, out2, NULL);
  exp_col_problem_fini(&problem);
  basic_fini(&basic);

  return status;
}

int main(int argc, char *argv[]) {
  const int bits = 6144;
  int b = 8;
  int T = 1;
  double start = 10.0;
  double stop = 14.1;
  double step = 0.5;
  double logp;
  int status;

  printf ("Testing derooij...\n");
  for (logp=start; logp < stop; logp += step) {
    int p = (int) pow(2.0, logp);
    status = test_derooij(bits, b, p, T);
    if (status > 0) {
      printf ("test failed!\n");
      goto cleanup;
    }
  }
  printf ("Done.\n");

  printf ("Testing derooij_wrap...\n");
  for (logp=start; logp < stop; logp += step) {
    int p = (int) pow(2.0, logp);
    status = test_derooij_wrap(bits, b, p, T);
    if (status > 0) {
      printf ("test failed!\n");
      goto cleanup;
    }
  }
  printf ("Done.\n");

  printf ("Testing yao...\n");
  for (logp=start; logp < stop; logp += step) {
    int p = (int) pow(2.0, logp);
    status = test_yao(bits, b, p, T);
    if (status > 0) {
      printf ("test failed!\n");
      goto cleanup;
    }
  }
  printf ("Done.\n");

  printf ("Testing yao_wrap...\n");
  for (logp=start; logp < stop; logp += step) {
    int p = (int) pow(2.0, logp);
    status = test_yao_wrap(bits, b, p, T);
    if (status > 0) {
      printf ("test failed!\n");
      goto cleanup;
    }
  }
  printf ("Done.\n");

  printf ("All tests ran successfully.\n");

cleanup:
  return 0;
}

    
