# TTG FX v0.5: Native analog ADC1 path — electrical hypothesis

## Source verification (4 November 2026)

Molecular's `device/hal.c` initializes `ADC1_MODULE` at 44,100Hz on the NDS32 main CPU using `AudioADC_DigitalInit(ADC1_MODULE,44100,adc1_fifo,sizeof adc1_fifo)`. Its input selection is `LINEIN3_LEFT_OR_MIC1` / `LINEIN3_RIGHT_OR_MIC2`: https://github.com/nzimas/molecular/blob/main/device/hal.c

The published MVsilicon BP1048 SDK implements `AudioADC1DataGet(void*,uint16_t)` using `DMA_CircularDataGet` and returns a count of **4-byte frames**. See https://github.com/leadercxn/bp1048_sdk_v0.1.12/blob/master/MVsB1_Base_SDK/driver/driver_api/src/adc_interface.c . The interface is confirmed; the Sonicake PCB's analog jack routing is **not** confirmed.

This is a potentially direct NDS32 ADC1 PCM path, independent of the NDS32->ARM SPIS output link, which remains in use. The 32-bit data format is assumed to hold L and R signed 16-bit samples; a recorded hardware capture is required to confirm.

## Implementation
- `src/ttg_input.c/h`: host-testable bridge for three sources: hard mute at boot, low-level 176Hz triangle, and **unverified ADC1**. Reads at most 32 DMA words per block, zero-fills a short read and counts frame/peak/underruns. No malloc or ISR-level blocking.
- `device/ttg_adc_adapter.c`: calls the SDK's real `AudioADC1DataGet`, no modification to the ADC setup.
- `device/ttg_main.c`: main application and RGB565 screen, USB recovery and diagnostic measurements, audio through actual Mutable chain. Two quick RIGHT releases cycle 0 MUTE→1 TONE→2 ADC→0; source always starts muted. The diagnostic top bar is red. Mute zeros output even with residual delay/reverb tails.
- `device/Makefile`: requires genuine Andes v3f FP+ ABI2, external Molecular GPL board HAL and MVsilicon SDK; cannot accept v3s silently. Produces a **raw application B .bin**, not a flashable HTFW update.
- `tests/test_input.c`: synthetic DMA tests. Tests do NOT prove source hardware routing.

## First hardware gate, once cross-built
1. Stock Sonicake V1.3.3 recovery and tested USB bootloader ready; output disconnected or minimum volume; no production flasher change.
2. Boot DIAG UI, confirm MUTE first. Explicitly test low-level tone, verify volume, then return to MUTE and verify recovery.
3. Switch to ADC source only with low-level signal, log `ADC read frames`, `ADC short reads` and `ADC peak L/R` via USB. Compare unplugged, 1kHz source plugged and increased source level.
4. If correlation fails, DO NOT publish an effects firmware; investigate routing via ARM. If it passes, establish exact PCM packing, stability, sample synchronization and latency before enabling general effect input.
5. CPU usage, UI display, gain safety, reverb tails, protection and USB recovery require physical sign-off.

The newly written DSP/UI sources are MIT; Molecular HAL is GPL-3.0-only when linked; vendor SDK and included preset data carry separate rights. Not a certified firmware, not a hardware experiment already performed.
