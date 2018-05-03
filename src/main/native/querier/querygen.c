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

#include "querygen.h"
#include <gmp.h>
#include <stdio.h>
#include <stdlib.h>

static void encquery_compute_lut(mpz_t **lut, int window_size, int num_windows, mont_t *mont, mpz_t g)
{
  int win, i;
  int W = (1<<window_size)-1;
  mpz_t gm, tmp, tmp1, tmp2, tmp3;

  mpz_init(gm);
  mpz_init(tmp);
  mpz_init(tmp1);
  mpz_init(tmp2);
  mpz_init(tmp3);

  mont_to_mont(mont, gm, g);
  for (win=0; win<num_windows; win++)
  {
    if (win > 0)
    {
      mpz_set(gm, tmp);
    }
    mpz_set(tmp, gm);
    for (i=0; i<W; i++)
    {
      mpz_init_set(lut[win][i], tmp);
      mont_multiply(mont, tmp, tmp, gm, tmp1, tmp2, tmp3);
    }
  }

  mpz_clear(gm);
  mpz_clear(tmp);
  mpz_clear(tmp1);
  mpz_clear(tmp2);
  mpz_clear(tmp3);
}

encquery_ctx_t* encquery_ctx_new(mpz_t p, mpz_t gen_p2, mpz_t q, mpz_t gen_q2, int window_size, int num_windows)
{
  int i;
  mpz_t p2, q2;
  mpz_t gcd, ap, aq;
  mpz_t tmp;
  encquery_ctx_t *ctx = (encquery_ctx_t*)calloc(1, sizeof(encquery_ctx_t));
  
  if (NULL == ctx) {
    return NULL;
  }
  mpz_init(p2);
  mpz_init(q2);
  mpz_init(gcd);
  mpz_init(ap);
  mpz_init(aq);
  mpz_init(tmp);

  mpz_init(ctx->N);
  mpz_mul(ctx->N, p, q);
  mpz_mul(p2, p, p);
  mpz_mul(q2, q, q);
  mpz_init(ctx->N2);
  mpz_mul(ctx->N2, p2, q2);
  mont_init(&ctx->mont_p2, p2);
  mont_init(&ctx->mont_q2, q2);
  mpz_init_set_ui(ctx->one_mont_p2, 1lu);
  mont_to_mont(&ctx->mont_p2, ctx->one_mont_p2, ctx->one_mont_p2);
  mpz_init_set_ui(ctx->one_mont_q2, 1lu);
  mont_to_mont(&ctx->mont_q2, ctx->one_mont_q2, ctx->one_mont_q2);

  mpz_gcdext(gcd, ap, aq, p2, q2);
  // TODO: check gcd = 1
  // 1 = ap * p^2 + aq * q^2
  mpz_init(ctx->e_mont_p2);
  mpz_mul(ctx->e_mont_p2, aq, q2);
  mpz_init(ctx->e_mont_q2);
  mpz_mul(ctx->e_mont_q2, ap, p2);
  
  mpz_init_set(ctx->gen_p2, gen_p2);
  mpz_init_set(ctx->gen_q2, gen_q2);
  ctx->window_size = window_size;
  ctx->num_windows = num_windows;
  // TODO: check that ctx->window_size divides 8 evenly
  ctx->wins_per_byte = 8 / ctx->window_size;
  ctx->winmask = (unsigned char)((1 << ctx->window_size) - 1);
  ctx->lut_p2 = (mpz_t**) calloc(num_windows, sizeof(mpz_t*));
  for (i=0; i<num_windows; i++)
  {
    ctx->lut_p2[i] = (mpz_t*) calloc((1<<window_size)-1, sizeof(mpz_t));
  }
  ctx->lut_q2 = (mpz_t**) calloc(num_windows, sizeof(mpz_t*));
  for (i=0; i<num_windows; i++)
  {
    ctx->lut_q2[i] = (mpz_t*) calloc((1<<window_size)-1, sizeof(mpz_t));
  }
  //printf ("generating table for p^2\n");
  encquery_compute_lut(ctx->lut_p2, window_size, num_windows, &ctx->mont_p2, gen_p2);
  //printf ("generating table for q^2\n");
  encquery_compute_lut(ctx->lut_q2, window_size, num_windows, &ctx->mont_q2, gen_q2);

  mpz_clear(p2);
  mpz_clear(q2);
  mpz_clear(gcd);
  mpz_clear(ap);
  mpz_clear(aq);
  mpz_clear(tmp);
  
  return ctx;
}

static void encrypt_for_prime(encquery_ctx_t *ctx, mont_t *mont, mpz_t one_mont, mpz_t **lut, mpz_t ansp, const unsigned char *rpbytes, int rpbyteslen, mpz_t tmp1, mpz_t tmp2, mpz_t tmp3)
{
  int win, nwins, byteindex, wins_per_byte, winvalue, window_size;
  unsigned char winmask, bytevalue;
  int i, j;

  window_size = ctx->window_size;
  wins_per_byte = ctx->wins_per_byte;
  winmask = ctx->winmask;

  mpz_set(ansp, one_mont);

  nwins = rpbyteslen * wins_per_byte;
  if (nwins > ctx->num_windows)
  {
    nwins = ctx->num_windows;
  }
      
  win = 0;
  for (j=0; j<rpbyteslen; j++)
  {
    byteindex = rpbyteslen - 1 - j;
    bytevalue = rpbytes[byteindex];
    for (i=0; i<wins_per_byte; i++)
    {
      winvalue = (int)(unsigned) (bytevalue & winmask);
      bytevalue >>= window_size;
      if (winvalue)
      {
	mont_multiply(mont, ansp, ansp, lut[win][winvalue-1], tmp1, tmp2, tmp3);
      }
      win += 1;
      if (win >= nwins)
	goto loop_done;
    }
  }
 loop_done:

  mont_from_mont(mont, ansp, ansp, tmp1, tmp2);
}

void encquery_encrypt(encquery_ctx_t *ctx, mpz_t ans, int bit_index, const unsigned char *rpbytes, int rpbyteslen, const unsigned char *rqbytes, int rqbyteslen)
{
  int win, nwins, byteindex, wins_per_byte, winvalue;
  unsigned char winmask;
  int i, j;
  mpz_t ansp, ansq, tmp, tmp2, tmp3;

  mpz_init(ansp);
  mpz_init(ansq);
  mpz_init(tmp);
  mpz_init(tmp2);
  mpz_init(tmp3);

  encrypt_for_prime(ctx, &ctx->mont_p2, ctx->one_mont_p2, ctx->lut_p2, ansp, rpbytes, rpbyteslen, tmp, tmp2, tmp3);
  encrypt_for_prime(ctx, &ctx->mont_q2, ctx->one_mont_q2, ctx->lut_q2, ansq, rqbytes, rqbyteslen, tmp, tmp2, tmp3);

  //gmp_printf ("from C: ansp = %Zd\n", ansp);
  //gmp_printf ("from C: ansq = %Zd\n", ansq);

  mpz_mul(ans, ctx->e_mont_p2, ansp);
  mpz_mul(tmp, ctx->e_mont_q2, ansq);
  mpz_add(ans, ans, tmp);
  mpz_fdiv_r(ans, ans, ctx->N2);

  // NOTE: this happens seldom enough that it's probably not worth optimizing
  // TODO: check that bit_index is not too large?
  if (bit_index >= 0)
  {
    mpz_mul_2exp(tmp, ctx->N, bit_index);
    mpz_add_ui(tmp, tmp, 1ul);
    mpz_fdiv_r(tmp, tmp, ctx->N2);
    mpz_mul(ans, ans, tmp);
    mpz_fdiv_r(ans, ans, ctx->N2);
  }

  mpz_clear(ansp);
  mpz_clear(ansq);
  mpz_clear(tmp);
  mpz_clear(tmp2);
  mpz_clear(tmp3);

  return;
}
