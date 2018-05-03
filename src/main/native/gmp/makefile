# 
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
# 
#   http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# 

GMP_VERSION?=6.1.1
GMP=gmp-$(GMP_VERSION)
BUILD_DIR?=build
INSTALL_DIR?=install

OS=$(shell uname | sed -e 's/CYGWIN.*/win32/g' \
                       -e 's/MINGW32.*/win32/g' \
                       -e 's/SunOS.*/sunos/g' \
                       -e 's/NetBSD/netbsd/g' \
                       -e 's/GNU\/kFreeBSD/kfreebsd/g' \
                       -e 's/FreeBSD/freebsd/g' \
                       -e 's/OpenBSD/openbsd/g' \
                       -e 's/Darwin.*/darwin/g' \
                       -e 's/AIX.*/aix/g' \
                       -e 's/Linux.*/linux/g')

ifeq ($(OS), darwin)
	LIBNAME=$(BUILD_DIR)/$(GMP)/libgmp.dylib
else
	LIBNAME=$(BUILD_DIR)/$(GMP)/libgmp.so
endif


all: $(LIBNAME)

$(LIBNAME): $(BUILD_DIR)/$(GMP)/gmp.h $(BUILD_DIR)/$(GMP)/Makefile
	$(MAKE) -C $(BUILD_DIR)/$(GMP)/ all check
		
$(BUILD_DIR)/$(GMP).tar.bz2:
	cd $(BUILD_DIR);curl -O https://ftp.gnu.org/gnu/gmp/$(GMP).tar.bz2

$(BUILD_DIR)/$(GMP)/configure: $(BUILD_DIR)/$(GMP).tar.bz2
	mkdir $(BUILD_DIR)/$(GMP)
	$(which sha1sum || which shasum) -c $(BUILD_DIR)/$(GMP).tar.bz2.sha512
	tar -xjf $< -C $(BUILD_DIR)/$(GMP) --strip-components 1
	touch -c $(BUILD_DIR)/$(GMP)/configure

$(BUILD_DIR)/$(GMP)/gmp.h $(BUILD_DIR)/$(GMP)/Makefile: $(BUILD_DIR)/$(GMP)/configure
	cd $(BUILD_DIR)/$(GMP); ./configure --prefix=$(INSTALL_DIR) --enable-fat --disable-static


install: $(LIBNAME)
	$(MAKE) -C $(BUILD_DIR)/$(GMP)/ install

