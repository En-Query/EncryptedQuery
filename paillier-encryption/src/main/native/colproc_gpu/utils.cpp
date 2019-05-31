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

#include <cassert>
#include <cstdint>
#include <iomanip>
#include <iostream>
#include <vector>

void dump(const std::vector<uint32_t> &v) {
  for (auto it=v.begin(); it != v.end(); it++) {
    std::cout << std::hex << std::setfill('0') << std::setw(8) << (*it) << std::dec << ", ";
  }
  std::cout << std::setfill(' ') << std::endl;
}

void dump(const uint32_t v[], size_t n) {
  for (size_t i = 0; i < n; i++) {
    std::cout << std::hex << std::setfill('0') << std::setw(8) << v[i] << std::dec << ", ";
  }
  std::cout << std::setfill(' ') << std::endl;
}

void dumpAsBI(const uint32_t v[], size_t n) {
  for (int i = (int)n-1; i >= 0; i--) {
    std::cout << std::hex << std::setfill('0') << std::setw(8) << v[i] << std::dec;
  }
  std::cout << std::setfill(' ') << std::endl;
}

void dump(const bin_t &bin) {
  std::cout << "bin " << bin.binnum << ":  ";
  for (auto it=bin.p.begin(); it != bin.p.end(); it++) {
    std::cout << static_cast<const void*>(*it) << ", ";
  }
  std::cout << std::setfill(' ') << std::endl;
}

void dump(const bins_t &bins) {
  std::cout << "--------------------" << std::endl;
  for (auto it=bins.l.begin(); it != bins.l.end(); it++) {
    dump(*it);
  }
  std::cout << "--------------------" << std::endl;
}
