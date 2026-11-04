# EXS / TTG FX v0.5 — standalone development application

**This is NOT a hardware firmware .bin and MUST NOT be flashed.**

- Entirely independent TTG FX 160×128 RGB565 graphics and controls (black, white, yellow).
- Real original MIT Emilie Gillet code in vendor/: stmlib SoftClip, stmlib SVF, Clouds Reverb.
- Standalone C++ DSP test path: D1 -> F1 -> F2 -> D2 -> original Clouds FxEngine Delay -> Clouds Reverb.
- Delay follows **MT4 P7.6.5/P7.6.7** (`exsiderurgica/EXS-FM1`): one genuine `clouds::FxEngine<16384, FORMAT_16_BIT>`, two 8,188-sample internal lines, **32 KiB** memory at 8 kHz and optional ping-pong cross-feedback. Mono mode is centered. The maximum ping-pong delay is approximately 1.02 s. The original Clouds Reverb runs independently.
- Each drive and each filter has independent controls. The original Mutable SVF selects LP or HP. Delay and reverb have separate wet controls.
- Main chip is Andes NDS32, ARM DSP is a separate processor. The MVsilicon SDK provides `AudioADC1DataGet` for NDS32 ADC1 circular DMA at 44.1kHz, which Molecular HAL already initializes. The guitar jack's electrical routing to this ADC remains UNVERIFIED.
- The BP10xx SDK and matching Andes v3f ABI2 FP+ toolchain are required to produce a hardware .bin; no safe device build is claimed.
- Original Molecular device HAL is GPL-3.0-only; this integration is MIT and must be relicensed appropriately if combined.
- No factory bootloader, second DSP firmware, main site or approved flasher hashes changed.

Host test (Linux, macOS, CI):

```sh
mkdir -p build
g++ -std=c++11 -O2 -Wall -Wextra -DTEST=1 -Ivendor src/mutable_chain.cpp tests/test_mutable_reverb.cpp -o build/test_mutable
./build/test_mutable
g++ -std=c++11 -O2 -Wall -Wextra -DTEST=1 -Ivendor src/mutable_chain.cpp tests/test_mutable_delay.cpp -o build/test_mutable_delay
./build/test_mutable_delay
cc -std=c11 -O2 -Wall -Wextra src/ttg_ui.c tests/test_ui.c -o build/test_ui
./build/test_ui
```

The downloadable v0.5 ZIP includes portable DSP, original UI, independent ADC bridge, diagnostic-only device entrypoint, SDK adapter, and hardware-gate documentation. It is **NOT** a flashable update. A proper v3f cross compiler is still missing; fake HAL tests are only syntax checks.

Mutable copyright: Emilie Gillet, 2012-2014; MIT notices retained verbatim in all official upstream vendored files.

## Native ADC experiment in v0.5

- `src/ttg_input.c/h`: starts in hard mute, optionally selects low-level test oscillator or reads ADC1 via callback. Unused/short DMA samples always zero; blocks limited to 32 frames.
- `device/ttg_adc_adapter.c`: real `AudioADC1DataGet()` call, as implemented in the public BP1048 SDK. The returned 32-bit words are **provisionally** decoded as two signed 16-bit samples.
- `device/ttg_main.c`: independent main app, RGB565 interface, TTG engine, USB-MIDI recovery, low-level input diagnostics. It hard-mutes the hardware output in source 0. Two fast RIGHT releases cycle MUTE→TONE→ADC→MUTE; red top banner always warns diagnostic-only.
- `device/Makefile`: compile only with matching Andes v3f ABI2 FP+ and external GPLv3 Molecular HAL / MVsilicon SDK; its output is **raw section-B app code**, not the factory HTFW update packet. Does not write ARM DSP firmware.
- `tests/test_input.c`: host verification for muted boot, test tone, DMA reading, short-read safety. GitHub CI checks source and fake HAL interfaces. **No Pocket Master hardware has been tested in this step.**

Read `docs/ADC_PATH.md`. The only remaining way to prove guitar input is to use an actual Pocket Master and a verified target build. No new hash has been approved in the live flasher.
