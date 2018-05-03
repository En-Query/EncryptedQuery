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
	LIBNAME=$(BUILD_DIR)/$(GMP)/libresponder.dylib
else
	LIBNAME=$(BUILD_DIR)/$(GMP)/libresponder.so
endif


JDK_INCLUDES=-I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/$(OS)
GMP_INSTALL_DIR?=gmp
BUILD_DIR?=build
INSTALL_DIR?=install


CFLAGS=-fPIC
CPPFLAGS=$(JDK_INCLUDES) -I$(GMP_INSTALL_DIR)/include
LDFLAGS=-lgmp -lm -L$(GMP_INSTALL_DIR)/lib

all: $(LIBNAME)
		
$(LIBNAME): $(BUILD_DIR)/basic.o $(BUILD_DIR)/yao.o $(BUILD_DIR)/yao_wrap.o $(BUILD_DIR)/yao_jni.o $(BUILD_DIR)/maxheap_derooij.o $(BUILD_DIR)/derooij.o $(BUILD_DIR)/derooij_wrap.o $(BUILD_DIR)/derooij_jni.o
	gcc $(CFLAGS) $(LDFLAGS) -shared -o $@ $^

$(BUILD_DIR)/basic.o: basic.c basic.h
	gcc $(CFLAGS) $(CPPFLAGS) -c $< -o $@

$(BUILD_DIR)/yao.o: yao.c yao.h
	gcc $(CFLAGS) $(CPPFLAGS) -c $< -o $@

$(BUILD_DIR)/yao_wrap.o: yao_wrap.c yao.h yao_wrap.h
	gcc $(CFLAGS) $(CPPFLAGS) -c $< -o $@

$(BUILD_DIR)/yao_jni.o: yao_jni.c yao.h yao_wrap.h yao_javah.h
	gcc $(CFLAGS) $(CPPFLAGS) -c $< -o $@

$(BUILD_DIR)/derooij.o: derooij.c derooij.h maxheap.h maxheap_defs_int.h
	gcc $(CFLAGS) $(CPPFLAGS) -c $< -o $@ -include maxheap_defs_derooij.h

$(BUILD_DIR)/derooij_wrap.o: derooij_wrap.c derooij.h derooij_wrap.h maxheap_defs_int.h
	gcc $(CFLAGS) $(CPPFLAGS) -c $< -o $@ -include maxheap_defs_derooij.h

$(BUILD_DIR)/derooij_jni.o: derooij_jni.c derooij.h derooij_wrap.h derooij_javah.h maxheap_defs_int.h
	gcc $(CFLAGS) $(CPPFLAGS) -c $< -o $@ -include maxheap_defs_derooij.h

$(BUILD_DIR)/maxheap_derooij.o: maxheap.c maxheap.h maxheap_defs_derooij.h
	gcc $(CFLAGS) $(CPPFLAGS) -c $< -o $@ -include maxheap_defs_derooij.h

$(BUILD_DIR)/test: test.c $(LIBNAME)
	gcc $(CFLAGS) $(CPPFLAGS) -o $@ $^ $(LDFLAGS) -include maxheap_defs_int.h

$(BUILD_DIR)/maxheap_int.o: maxheap.c maxheap.h maxheap_defs_int.h
	gcc $(CFLAGS) $(CPPFLAGS) -c $< -o $@ -include maxheap_defs_int.h

$(BUILD_DIR)/test_maxheap: test_maxheap.c maxheap_int.o maxheap_defs_int.h
	gcc $(CFLAGS) $(CPPFLAGS) -o $@ $< maxheap_int.o $(LDFLAGS) -include maxheap_defs_int.h

	
install: $(LIBNAME)
	cp $(LIBNAME) $(INSTALL_DIR)