# Pocket Master / TTG FX: hardware gate

## What changed
Molecular documents:
- Main processor: MVsilicon BP10xx / Andes NDS32.
- FX processor: ARM Cortex-M4F, controlled by UART0 at 460800 baud.
- SPIS: 512-byte full-duplex transfers. The first 256 *transmitted* bytes contain 64 stereo signed 16-bit frames @ 44.1 kHz.
- The 512 **received** bytes remain unexplained in Molecular's public code. No evidence yet that NDS32 can read line-in samples.
- Molecular installs only main application section 'b' and retains bootloader and stock ARM FX firmware.
- Device build requires a separately obtained, appropriately licensed MVsilicon SDK and Andes nds32le toolchain.

Sources: https://github.com/nzimas/molecular/blob/main/device/hal.c and https://github.com/nzimas/molecular/blob/main/device/armlink.c

## Hardware diagnostic: no audio write until input access confirmed
1. Keep official Sonicake 1.3.3 BIN and known rollback route available.
2. In a separate licensed Molecular checkout, use the SPIS RX DMA-done path to copy one *completed* 512-byte receive transfer every 32-64 transfers into a tiny bounded queue. Never send USB output or block inside ISR. Continue normal audio output.
3. Drain the queue only from a non-real-time loop to a host over USB, preserving complete 512-byte record boundaries. Bound bandwidth and overflow.
4. Capture unplugged input (silence), 1 kHz input at two amplitudes, and note times/levels; verify RX signal follows them consistently. No claims from an isolated RMS value.
5. Use `python3 tools/analyze_spis.py capture.bin` to inspect both possible 256-byte halves as 16-bit interleaved stereo and compare, then analyze alternative layouts as needed.
6. If actual input samples can be decoded and routed, test inline bypass first, then D1, F1, F2, D2, DELAY, REV in order, with CPU and underrun instrumentation. If not, explore ARM-side processing or another channel.
7. Only after hardware validation develop an application-only installable build and independent recovery validation.

Do not overwrite region 'c', bootloader, production installer, or approved firmware SHA list during the probe. Prototype does not include vendor binaries, proprietary SDK or Sonicake presets. Licensing of any Molecular-derived code: GPL-3.0-only.

## Added capture module
`src/spis_probe.{h,c}` is a bounded capture ring, sampling one completed 512-byte SPI RX frame every 32 transfers. It drops frames when full instead of blocking; any USB output must happen later in the foreground, not inside an ISR. `tests/test_spis_probe.c` verifies decimation, queue integrity and overflow. Hardware DMA hook, transport, cache coherency and target CPU/latency validation are still missing. Compiler barrier assumes an in-order single-core device with atomic aligned index reads/writes.
