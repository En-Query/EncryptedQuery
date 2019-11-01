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
	LIBNAME=$(BUILD_DIR)/libseal.dylib
else
	LIBNAME=$(BUILD_DIR)/libseal.so
endif

JDK_INCLUDES=-I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/$(OS) -I$(JNI_HEADERS_DIR) -I$(SEAL_HEADERS_DIR) 


GPP=g++
CFLAGS= -std=c++17 -pthread -Wall -Wextra -Werror -Ofast -fpic $(JDK_INCLUDES)
 
all: $(LIBNAME)

$(LIBNAME): $(BUILD_DIR)/SEALCpp.o $(BUILD_DIR)/SEALCppHelper.o $(BUILD_DIR)/plainmul128.o $(BUILD_DIR)/smallntt.o
	$(GPP) -shared -o $@ $^ $(SEAL_LIB)

$(BUILD_DIR)/SEALCpp.o: SEALCpp.cpp  
	$(GPP) $(CFLAGS) -c $< -o $@

$(BUILD_DIR)/SEALCppHelper.o: SEALCppHelper.cpp
	$(GPP) $(CFLAGS) -c $< -o $@

$(BUILD_DIR)/plainmul128.o: plainmul128.cpp 
	$(GPP) $(CFLAGS) -c $< -o $@

$(BUILD_DIR)/smallntt.o: smallntt.cpp 
	$(GPP) $(CFLAGS) -c $< -o $@


.PHONY: clean debug fromclean

clean:
	rm -f $(TARGET) $(OBJ) $(PIC)

debug:
	$(GPP) $(SRC) $(LIB) $(CFLAGS) $(DBGFLAGS) -o $(TARGET)

fromclean:
	make clean && make
