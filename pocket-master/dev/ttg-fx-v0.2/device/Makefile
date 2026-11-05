# EXS TTG FX Pocket Master DIAGNOSTIC native build, UNVERIFIED on hardware.
# External Molecular GPL3 HAL, external MVsilicon SDK and genuine Andes v3f.
# Do not flash artifacts until input, clock, RAM map and recovery pass hardware gate.
MOL ?= $(abspath ../../../../../../molecular)
SDK ?= $(abspath ../../../../../../sdk/bp1048_sdk/MVsB1_Base_SDK)
CC := nds32le-elf-gcc
CXX := nds32le-elf-g++
OBJCOPY := nds32le-elf-objcopy
OBJDUMP := nds32le-elf-objdump
OUT := ../build/device
ARCH := -mcmodel=medium -mcpu=d1088-spu -mext-dsp -mext-zol
INCS := -I$(SDK)/driver/driver/inc -I$(SDK)/driver/driver_api/inc -I$(SDK)/middleware/mv_utils/inc -I$(MOL)/device -I../src -I../vendor -I.
CFLAGS := -O2 -g -Wall -Wextra $(ARCH) $(INCS) -ffunction-sections -fdata-sections -DFUNC_OS_EN=0 -DHOST=1 -DTTG_DEVICE_DIAGNOSTIC_ONLY=1 -DUSER_DEFINE_MCLK_112896M_DIV_VALUE=11289600 -DUSER_DEFINE_MCLK_12288M_DIV_VALUE=12288000
CXXFLAGS := $(CFLAGS) -std=c++11 -DTEST=1 -fno-exceptions -fno-rtti
LINK := $(ARCH) -nostartfiles -static -L$(SDK)/driver/driver -T$(MOL)/device/app.ld -Wl,--gc-sections -mvh -Wl,-Map,$(OUT)/ttgfx.map
C_SRCS := ttg_main.c ttg_adc_adapter.c ../src/ttg_input.c ../src/ttg_app.c ../src/ttg_ui.c $(MOL)/device/hal.c $(MOL)/device/usbmidi.c $(MOL)/device/armlink.c $(MOL)/device/startup/init.c $(SDK)/startup/interrupt.c $(SDK)/startup/retarget.c $(SDK)/driver/driver_api/src/dac_interface.c $(SDK)/driver/driver_api/src/adc_interface.c
COBJS := $(addprefix $(OUT)/obj/,$(addsuffix .o,$(notdir $(basename $(C_SRCS)))))
OBJS := $(COBJS) $(OUT)/obj/mutable_chain_cpp.o $(OUT)/obj/crt0.o
vpath %.c . ../src $(MOL)/device $(MOL)/device/startup $(SDK)/startup $(SDK)/driver/driver_api/src
.PHONY: all guard clean
all: guard $(OUT)/ttgfx_diag.bin
guard:
	@command -v $(CC) >/dev/null || { echo 'BLOCKED: Andes nds32le-elf-mculib-v3f compiler missing'; exit 1; }
	@command -v $(CXX) >/dev/null || { echo 'BLOCKED: matching Andes v3f C++ compiler missing'; exit 1; }
	@$(CC) -v 2>&1 | grep -qi 'v3f' || { echo 'BLOCKED: compiler does not identify as Andes v3f ABI2 FP+'; exit 1; }
	@test -f $(MOL)/device/hal.c -a -f $(MOL)/device/app.ld || { echo 'BLOCKED: Molecular GPL board HAL/linker missing'; exit 1; }
	@test -f $(SDK)/driver/driver/libDriver.a || { echo 'BLOCKED: MVsilicon SDK libDriver.a missing'; exit 1; }
	@test -f ../vendor/clouds/dsp/fx/fx_engine.h || { echo 'BLOCKED: original MIT Mutable headers absent'; exit 1; }
$(OUT)/obj/%.o: %.c
	@mkdir -p $(OUT)/obj
	$(CC) $(CFLAGS) -c $< -o $@
$(OUT)/obj/mutable_chain_cpp.o: ../src/mutable_chain.cpp
	@mkdir -p $(OUT)/obj
	$(CXX) $(CXXFLAGS) -c $< -o $@
$(OUT)/obj/crt0.o: $(SDK)/startup/crt0.S
	@mkdir -p $(OUT)/obj
	$(CC) $(CFLAGS) -c $< -o $@
$(OUT)/ttgfx_diag.adx: $(OBJS)
	$(CXX) $(LINK) -o $@ $^ -lDriver -ldsp -lm
	@if $(OBJDUMP) -d $@ | grep -qE '\\s(faddd|fsubd|fmuld|fdivd|fsqrtd|fcm[a-z]+d)\\s'; then echo 'ERROR: CPU has no double-precision FPU'; rm -f $@; exit 1; fi
$(OUT)/ttgfx_diag.bin: $(OUT)/ttgfx_diag.adx
	$(OBJCOPY) -S -O binary $< $@
	nds32le-elf-size $(OUT)/ttgfx_diag.adx
	@echo 'DIAGNOSTIC ONLY: APP B RAW BIN, NOT HTFW! NO PUBLIC FLASH ALLOWLIST UPDATE.'
clean:
	rm -rf $(OUT)
