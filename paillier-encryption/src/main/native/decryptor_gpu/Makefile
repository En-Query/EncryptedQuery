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
#   XMP_DIR
#   CUDA_INSTALL_DIR
#   BUILD_DIR
#   (TARGET_RPATH)

JAVA_HOME ?= /opt/jdk1.8.0_171
CUDA_INSTALL_DIR ?= /usr/local/cuda
BUILD_DIR ?= .
TARGET_RPATH ?= '$$ORIGIN'


xmp_incdir ?= $(XMP_DIR)/src/include
xmp_libdir ?= $(XMP_DIR)
cuda_incdir ?= $(CUDA_INSTALL_DIR)/include
cuda_libdir ?= $(CUDA_INSTALL_DIR)/lib64
gmp_incdir ?= $(GMP_INSTALL_DIR)/include
gmp_libdir ?= $(GMP_INSTALL_DIR)/lib

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

jdk_includes=-I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/$(os)

xmp_incdir ?= $(XMP_DIR)/src/include
xmp_libdir ?= $(XMP_DIR)
cuda_incdir ?= $(CUDA_INSTALL_DIR)/include
cuda_libdir ?= $(CUDA_INSTALL_DIR)/lib64
gmp_incdir ?= $(GMP_INSTALL_DIR)/include
gmp_libdir ?= $(GMP_INSTALL_DIR)/lib

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

jdk_includes=-I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/$(os)

cxx=g++
cxxflags=-std=c++11 -O0 -g -fPIC
#cxxflags=-std=c++11 -O3 -fPIC
cppflags = -I$(gmp_incdir) -I$(xmp_incdir) -I$(cuda_incdir)
ldflags = -L$(gmp_libdir) -L$(xmp_libdir) -L$(cuda_libdir)

ifneq ($(origin TARGET_RPATH), undefined)
rpath_ldflags = -Wl,-rpath=$(TARGET_RPATH)
endif


all: $(BUILD_DIR)/libgpudecryptor.so $(BUILD_DIR)/test_gpudecryptor_dl.exe

$(BUILD_DIR)/gpudecryptor.o: gpudecryptor.cpp gpudecryptor.h
	$(cxx) $(cxxflags) $(cppflags) -c -o $@ $<

$(BUILD_DIR)/gpudecryptor_jni.o: gpudecryptor_jni.cpp
	$(cxx) $(cxxflags) $(cppflags) $(jdk_includes) -c -o $@ $<

$(BUILD_DIR)/libgpudecryptor.so: $(BUILD_DIR)/gpudecryptor.o $(BUILD_DIR)/gpudecryptor_jni.o
	$(cxx) $(cxxflags) $(cppflags) -shared -o $@ $^ $(ldflags) -lgmp -lxmp -lcudart $(rpath_ldflags)

$(BUILD_DIR)/test_gpudecryptor_dl.exe: $(BUILD_DIR)/test_gpudecryptor.cpp $(BUILD_DIR)/libgpudecryptor.so
	$(cxx) $(cxxflags) $(cppflags) -o $@ $^ $(ldflags) -lgmp -lxmp -lcudart

test: $(BUILD_DIR)/test_gpudecryptor_dl.exe
	LD_LIBRARY_PATH=. $(BUILD_DIR)/test_gpudecryptor_dl.exe

clean:
	rm -f *.o *.so *.exe

