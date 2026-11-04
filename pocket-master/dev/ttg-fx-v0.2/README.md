# EXS / TTG FX v0.2 — standalone development application

**This is NOT a hardware firmware .bin and MUST NOT be flashed.**

- Entirely independent TTG FX 160×128 RGB565 graphics and controls (black, white, yellow).
- Real original MIT Emilie Gillet code in vendor/: stmlib SoftClip, stmlib SVF, Clouds Reverb.
- Standalone C++ DSP test path: D1 -> F1 -> F2 -> D2 -> original Clouds FxEngine Delay -> Clouds Reverb.
- Delay follows **MT4 P7.6.5/P7.6.7** (`exsiderurgica/EXS-FM1`): one genuine `clouds::FxEngine<16384, FORMAT_16_BIT>`, two 8,188-sample internal lines, **32 KiB** memory at 8 kHz and optional ping-pong cross-feedback. Mono mode is centered. The maximum ping-pong delay is approximately 1.02 s. The original Clouds Reverb runs independently.
- DSP interface is still provisional: F1/F2 share cutoff/Q and D1/D2 share gain; filters are LP in this host stage.
- Main chip is Andes NDS32, ARM DSP is a separate processor. Its SPIS RX data has not yet been proven to carry real analog input.
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

The user-downloadable ZIP includes source, UI preview, SPIS RX probe and hardware-gate documentation. This is a **development package**, not a flashable update.

Mutable copyright: Emilie Gillet, 2012-2014; MIT notices retained verbatim in all official upstream vendored files.
