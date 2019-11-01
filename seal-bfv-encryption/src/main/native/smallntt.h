#ifndef SMALLNTT
#include <iostream>
#include <fstream>
#include <iomanip>
#include <vector>
#include <string>
#include <chrono>
#include <random>
#include <thread>
#include <mutex>
#include <memory>
#include <limits>
#include "seal/seal.h"
#include "seal/context.h"

using namespace std;
using namespace seal;

#define SMALLNTT
void smallntt(Plaintext &plain,util::SmallNTTTables* tables,size_t dim,
shared_ptr<const SEALContext::ContextData> context);

void decompose(const SEALContext::ContextData &context_data, 
            const std::uint64_t *value, std::uint64_t *destination, util::MemoryPool &pool);
 
void tablegen(util::SmallNTTTables* outtables,shared_ptr<const SEALContext::ContextData> context,size_t logdim);

#endif
