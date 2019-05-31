#
# EncryptedQuery is an open source project allowing user to query databases with queries under
# homomorphic encryption to securing the query and results set from database owner inspection.
# Copyright (C) 2018 EnQuery LLC
#
# This program is free software: you can redistribute it and/or modify it under the terms of the
# GNU Affero General Public License as published by the Free Software Foundation, either version 3
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
# even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License along with this program.
# If not, see <https://www.gnu.org/licenses/>.
#


# Caller may need to externally set the following variables:
#   JAVA_HOME
#   GMP_INSTALL_DIR
#   BUILD_DIR
#   JNI_HEADERS_DIR
#   (TARGET_RPATH)

BUILD_DIR ?= '.'
TARGET_RPATH ?= '$$ORIGIN'


ifneq ($(origin GMP_INSTALL_DIR), undefined)
gmp_incdir ?= $(GMP_INSTALL_DIR)/include
gmp_libdir ?= $(GMP_INSTALL_DIR)/lib
endif
ifneq ($(origin gmp_incdir), undefined)
gmp_incs ?= -I$(gmp_incdir)
endif
ifneq ($(origin gmp_libdir), undefined)
gmp_ldflags ?= -L$(gmp_libdir)
endif

os=$(shell uname | sed -e 's/CYGWIN.*/win32/g' \
                       -e 's/MINGW32.*/win32/g' \
                       -e 's/SunOS.*/sunos/g' \
                       -e 's/NetBSD/netbsd/g' \
                       -e 's/GNU\/kFreeBSD/kfreebsd/g' \
                       -e 's/FreeBSD/freebsd/g' \
                       -e 's/OpenBSD/openbsd/g' \
                       -e 's/Darwin.*/darwin/g' \
                       -e 's/AIX.*/aix/g' \
                       -e 's/Linux.*/linux/g')

ifeq ($(os), darwin)
	libname = libresponder.dylib
else
	libname = libresponder.so
endif

jdk_includes = -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/$(os) -I$(JNI_HEADERS_DIR)

cc = gcc
cflags = -fPIC
cppflags = $(jdk_includes) $(gmp_incs)
ldflags = $(gmp_ldflags)

ifneq ($(origin TARGET_RPATH), undefined)
rpath_ldflags = -Wl,-rpath=$(TARGET_RPATH)
endif


all: $(BUILD_DIR)/$(libname)

$(BUILD_DIR)/$(libname): $(BUILD_DIR)/basic.o $(BUILD_DIR)/yao.o $(BUILD_DIR)/yao_wrap.o $(BUILD_DIR)/yao_jni.o $(BUILD_DIR)/maxheap_derooij.o $(BUILD_DIR)/derooij.o $(BUILD_DIR)/derooij_wrap.o $(BUILD_DIR)/derooij_jni.o
ifeq ($(os), darwin)
	$(cc) $(cflags) -shared -o $@ $^ $(ldflags) -lgmp -lm
	test -f $(gmp_libdir)/libgmp.10.dylib
	install_name_tool -change $(gmp_libdir)/libgmp.10.dylib @loader_path/libgmp.dylib $(BUILD_DIR)/$(libname)
else
	$(cc) $(cflags) -shared -o $@ $^ $(ldflags) -lgmp -lm $(rpath_ldflags)
endif

$(BUILD_DIR)/basic.o: basic.c
	$(cc) $(cflags) $(cppflags) -c $< -o $@

$(BUILD_DIR)/yao.o: yao.c
	$(cc) $(cflags) $(cppflags) -c $< -o $@

$(BUILD_DIR)/yao_wrap.o: yao_wrap.c
	$(cc) $(cflags) $(cppflags) -c $< -o $@

$(BUILD_DIR)/yao_jni.o: yao_jni.c
	$(cc) $(cflags) $(cppflags) -c $< -o $@

$(BUILD_DIR)/derooij.o: derooij.c
	$(cc) $(cflags) $(cppflags) -c $< -o $@ -include maxheap_defs_derooij.h

$(BUILD_DIR)/derooij_wrap.o: derooij_wrap.c
	$(cc) $(cflags) $(cppflags) -c $< -o $@ -include maxheap_defs_derooij.h

$(BUILD_DIR)/derooij_jni.o: derooij_jni.c
	$(cc) $(cflags) $(cppflags) -c $< -o $@ -include maxheap_defs_derooij.h

$(BUILD_DIR)/maxheap_derooij.o: maxheap.c
	$(cc) $(cflags) $(cppflags) -c $< -o $@ -include maxheap_defs_derooij.h

$(BUILD_DIR)/test: test.c $(BUILD_DIR)/$(libname)
	$(cc) $(cflags) $(cppflags) -o $@ $^ $(ldflags) -include maxheap_defs_int.h

$(BUILD_DIR)/maxheap_int.o: maxheap.c
	$(cc) $(cflags) $(cppflags) -c $< -o $@ -include maxheap_defs_int.h

$(BUILD_DIR)/test_maxheap: test_maxheap.c maxheap_int.o
	$(cc) $(cflags) $(cppflags) -o $@ $< maxheap_int.o $(ldflags) -include maxheap_defs_int.h
