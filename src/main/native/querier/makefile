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
	LIBNAME=$(BUILD_DIR)/$(GMP)/libquerygen.dylib
else
	LIBNAME=$(BUILD_DIR)/$(GMP)/libquerygen.so
endif

                       
JDK_INCLUDES=-I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/$(OS) -I$(JNI_HEADERS_DIR)
GMP_INSTALL_DIR?=
BUILD_DIR?=build
INSTALL_DIR?=install

CFLAGS=-fPIC
CPPFLAGS=$(JDK_INCLUDES) -I$(GMP_INSTALL_DIR)/include
LDFLAGS=-lgmp -L$(GMP_INSTALL_DIR)/lib

all: $(LIBNAME)

$(LIBNAME): $(BUILD_DIR)/querygen.o $(BUILD_DIR)/mont.o $(BUILD_DIR)/jniwrapper.o
	gcc $(CFLAGS) $(LDFLAGS) -shared -o $@ $^

$(BUILD_DIR)/querygen.o: querygen.c
	gcc $(CFLAGS) $(CPPFLAGS) -c $< -o $@

$(BUILD_DIR)/mont.o: mont.c
	gcc $(CFLAGS) $(CPPFLAGS) -c $< -o $@

$(BUILD_DIR)/jniwrapper.o: jniwrapper.c 
	gcc $(CFLAGS) $(CPPFLAGS) -c $< -o $@


install: $(LIBNAME)
	cp $(LIBNAME) $(INSTALL_DIR)


