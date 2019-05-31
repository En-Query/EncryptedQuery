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

#ifndef UTILS_H
#define UTILS_H

#include <cstdint>
#include <vector>

#include <string.h>
#include <time.h>

__inline__ uint64_t time_now() {
  struct timespec tp;
  clock_gettime(CLOCK_REALTIME, &tp);
  return (uint64_t) tp.tv_sec * 1000000000u + tp.tv_nsec;
}

void dump(const std::vector<uint32_t> &v);
void dump(const uint32_t v[], size_t n);
void dumpAsBI(const uint32_t v[], size_t n);

#endif // UTILS_H
