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

#ifndef RESOURCES_H
#define RESOURCES_H

#include <condition_variable>
#include <iostream>
#include <memory>
#include <mutex>
#include <vector>

typedef enum policy_t {
    RP_POLICY_WAIT,
    RP_POLICY_CALLER_RUNS,
    RP_POLICY_ABORT,
    RP_POLICY_GPU_NOW,
    RP_NUM_POLICIES
} policy_t;

static inline policy_t busyPolicyFromString(std::string &policyStr, policy_t defaultPolicy) {
    if (policyStr == "Wait") {
	return RP_POLICY_WAIT;
    } else if (policyStr == "CallerRuns") {
	return RP_POLICY_CALLER_RUNS;
    } else if (policyStr == "Abort") {
	return RP_POLICY_ABORT;
    } else if (policyStr == "GPUNow") {
	return RP_POLICY_GPU_NOW;
    } else {
	return defaultPolicy;
    }
}

template <typename T>
struct resource_t {
    //std::mutex m;
    bool busy;
    std::shared_ptr<T> v;
    resource_t(std::shared_ptr<T> v) : busy(false), v(v) {};
    std::shared_ptr<T> get() { return v; }
    bool is_null() { return !v; }
};

template <typename T>
struct pool_t {
    std::mutex m;
    std::vector<resource_t<T> > vv;
    policy_t policy;
    std::mutex m_busy;
    std::condition_variable cv_busy;
    size_t ready_count;
    resource_t<T> dummy;
    pool_t(policy_t policy) : policy(policy), dummy(std::shared_ptr<T>()) {};
    void set_policy(policy_t policy) {
	std::lock_guard<std::mutex> guard(m);
	this->policy = policy;
    }
    size_t size() { return vv.size(); };
    void insert(std::shared_ptr<T> v) {
	std::lock_guard<std::mutex> guard(m);
	std::lock_guard<std::mutex> guard2(m_busy);
	vv.emplace_back(v);
	ready_count++;
	cv_busy.notify_one();
    }
    resource_t<T> &request() {
	std::unique_lock<std::mutex> lk(m_busy);
	cv_busy.wait(lk, [this]{ return (policy != RP_POLICY_WAIT) || ready_count > 0; });
	// Look for first free value.  If found, decrement
	// ready_count, mark the resource as busy and return it.
	for (auto it=vv.begin(); it!=vv.end(); it++) {
	    resource_t<T> &r = *it;
	    if (!r.busy) {
		r.busy = true;
		ready_count--;
		return r;
	    }
	}
	return this->dummy;
    }
    void release(resource_t<T> &r) {
	if (r.is_null()) return;
	std::unique_lock<std::mutex> lk(m_busy);
	r.busy = false;
	ready_count++;
	lk.unlock();
	cv_busy.notify_one();
    }
    void clear() {
	std::lock_guard<std::mutex> guard(m);
	std::lock_guard<std::mutex> guard2(m_busy);
	vv.clear();
	ready_count = 0;
    }
};

#endif // RESOURCES_H
