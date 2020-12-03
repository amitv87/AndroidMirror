WORKER_JS = worker.js
CFLAGS_OPT = -O3 -g0
STRIP_LOG_FLAG = -include ../strip_log.h
EMCC_OPT = WASM=1 VERBOSE=0 SINGLE_FILE=0 NO_FILESYSTEM=1 NO_EXIT_RUNTIME=1 INITIAL_MEMORY=33554432
EMCC_FLAGS = $(CFLAGS_OPT) $(EMCC_OPT:%=-s %) --bind --post-js $(WORKER_JS) --memory-init-file 0 --minify 0

rwildcard = $(wildcard $1$2) $(foreach d,$(wildcard $1*),$(call rwildcard,$d/,$2))

LIBAV_DIR = libav

OPENH264_DIR = openh264
OPENH264_CFLAGS = $(CFLAGS_OPT) -Wno-missing-declarations -Wno-unused-variable $(STRIP_LOG_FLAG)
OPENH264_MAKE_CMD = emmake make OS=linux ARCH=asmjs BUILDTYPE=Release -j$(NUM_CPU)

WASM_OUT_DIR := $(if $(WASM_OUT_DIR),$(WASM_OUT_DIR),wasm)

.PHONY : all clean list

all : $(WASM_OUT_DIR)/libav.js $(WASM_OUT_DIR)/openh264.js

clean :
	cd $(LIBAV_DIR) && emmake make clean
	cd $(OPENH264_DIR) && $(OPENH264_MAKE_CMD) clean

list:
	@$(MAKE) -pRrq -f $(lastword $(MAKEFILE_LIST)) : 2>/dev/null \
		| awk -v RS= -F: '/^# File/,/^# Finished Make data base/ {if ($$1 !~ "^[#.]") {print $$1}}' \
		| sort | egrep -v -e '^[^[:alnum:]]' -e '^$@$$'

$(WASM_OUT_DIR)/libav.js : libav_decoder.cpp $(LIBAV_DIR)/libavcodec/libavcodec.a
	@echo "\033[92mBuilding $@\033[0m"
	emcc -I $(LIBAV_DIR) $(EMCC_FLAGS) -o $@ $^ $(LIBAV_DIR)/libavutil/libavutil.a

$(WASM_OUT_DIR)/openh264.js : openh264_decoder.cpp $(OPENH264_DIR)/libdecoder.a
	@echo "\033[92mBuilding $@\033[0m"
	emcc -I $(OPENH264_DIR)/codec/api/svc $(EMCC_FLAGS) -o $@ $^ $(OPENH264_DIR)/libcommon.a

$(OPENH264_DIR)/libdecoder.a :
	@echo "\033[92mBuilding $@\033[0m"
	cd $(OPENH264_DIR) && $(OPENH264_MAKE_CMD) CFLAGS_OPT="$(OPENH264_CFLAGS)" libdecoder.a libcommon.a

$(LIBAV_DIR)/libavcodec/libavcodec.a :
	@echo "\033[92mBuilding $@\033[0m"
	cd $(LIBAV_DIR) && bash version.sh ./ avversion.h && emmake make -j$(NUM_CPU)
