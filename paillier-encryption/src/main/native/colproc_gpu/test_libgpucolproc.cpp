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

#include <gmp.h>

#include <dlfcn.h>

#include <cassert>
#include <chrono>
#include <cstdlib>
#include <iostream>
#include <thread>

using namespace std;

initialize_native_library_t *p_initialize_native_library;
close_native_library_t *p_close_native_library;
create_query_t *p_create_query;
query_set_qelt_t *p_query_set_qelt;
remove_query_t *p_remove_query;
create_colproc_t *p_create_colproc;
colproc_insert_chunk_t *p_colproc_insert_chunk;
colproc_compute_and_clear_t *p_colproc_compute_and_clear;

void do_test2() {

  const size_t w = 1;
  mpz_t N;
  mpz_init_set_str(N, "deadbeef", 16);
  const uint32_t num_qelts = 2;
  const char *qelts_str[num_qelts] = {
    "01234567" "cafebabe",
    "89abcdef" "0badf00d",
  };
  const char expected_str[] = "7304860121843791858";

  handle_t h_query = p_create_query("test_uuid2", N, num_qelts);
  assert (h_query != 0);
  mpz_t qelt;
  mpz_init(qelt);
  for (size_t i = 0; i < num_qelts; i++) {
    mpz_set_str(qelt, qelts_str[i], 16);
    p_query_set_qelt(h_query, i, qelt);
  }
  mpz_clear(qelt);
  handle_t h_colproc = p_create_colproc(h_query);

  assert (p_colproc_insert_chunk(h_colproc, 0, 3));
  assert (p_colproc_insert_chunk(h_colproc, 1, 4));

  mpz_t result, expected;
  mpz_init(result);
  mpz_init_set_str(expected, expected_str, 10);
  assert (!p_colproc_compute_and_clear(h_colproc, result));
  cout << "done." << endl;
  gmp_printf ("result = %Zd (0x%Zx)\n", result, result);
  assert (!mpz_cmp(result, expected));
  cout << "result matches expected answer" << endl;
  mpz_clear(result);
  mpz_clear(expected);

  cout << "removing query" << endl;
  bool b = p_remove_query(h_query);
  cout << "finished removing query" << endl;
  assert(b);
}

int main(int argc, char *argv[]) {

  cout << "Hello, world!" << endl;

  assert (argc == 2);
  const char *libname = argv[1];
  char *error;

  cout << "loading library " << libname << endl;
  void *h_lib = dlopen(libname, RTLD_LAZY);
  if (!h_lib) {
    cout << dlerror() << endl;
    exit(EXIT_FAILURE);
  }
  dlerror();    /* Clear any existing error */
  cout << "done." << endl;
  
  p_initialize_native_library = (initialize_native_library_t*) dlsym(h_lib, "initialize_native_library");
  error = dlerror();
  if (error != NULL) {
    fprintf(stderr, "%s\n", error);
    exit(EXIT_FAILURE);
  }

  p_close_native_library = (close_native_library_t*) dlsym(h_lib, "close_native_library");
  error = dlerror();
  if (error != NULL) {
    fprintf(stderr, "%s\n", error);
    exit(EXIT_FAILURE);
  }

  p_create_query = (create_query_t*) dlsym(h_lib, "create_query");
  error = dlerror();
  if (error != NULL) {
    fprintf(stderr, "%s\n", error);
    exit(EXIT_FAILURE);
  }

  p_query_set_qelt = (query_set_qelt_t*) dlsym(h_lib, "query_set_qelt");
  error = dlerror();
  if (error != NULL) {
    fprintf(stderr, "%s\n", error);
    exit(EXIT_FAILURE);
  }

  p_remove_query = (remove_query_t*) dlsym(h_lib, "remove_query");
  error = dlerror();
  if (error != NULL) {
    fprintf(stderr, "%s\n", error);
    exit(EXIT_FAILURE);
  }

  p_create_colproc = (create_colproc_t*) dlsym(h_lib, "create_colproc");
  error = dlerror();
  if (error != NULL) {
    fprintf(stderr, "%s\n", error);
    exit(EXIT_FAILURE);
  }

  p_colproc_insert_chunk = (colproc_insert_chunk_t*) dlsym(h_lib, "colproc_insert_chunk");
  error = dlerror();
  if (error != NULL) {
    fprintf(stderr, "%s\n", error);
    exit(EXIT_FAILURE);
  }

  p_colproc_compute_and_clear = (colproc_compute_and_clear_t*) dlsym(h_lib, "colproc_compute_and_clear");
  error = dlerror();
  if (error != NULL) {
    fprintf(stderr, "%s\n", error);
    exit(EXIT_FAILURE);
  }

  do_test2();

  do_test2();

  do_test2();

  dlclose(h_lib);

  cout << "sleeping before exiting" << endl;
  std::chrono::seconds timespan(5);
  std::this_thread::sleep_for(timespan);
  cout << "goodbye" << endl;

  return 0;
}

