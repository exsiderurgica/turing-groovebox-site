# TTG FX — Pocket Master | DSP host prototype v0.1 (4 Nov 2026)

**Not a flashable firmware. Production flasher and accepted SHA allow-list are unchanged.**

An independently written, portable 44.1 kHz stereo C audio chain:
`ANALOG IN → D1 → F1 → F2 → D2 → DELAY → REVERB → OUT`.

Included: saturation with individual bypass/gain/level, LP/HP resonant state-variable filters, stereo half-rate 16-bit delay (~740 ms), simple four-comb stereo reverb. **Delay is not yet Mutable and reverb is not Clouds.** DSP state: ~91 KB, static/global. No dynamic allocation.

Local test: `sh build.sh`; requires C11 compiler. Tests passed offline (passthrough, silence, bounded output, delay impulse, reverb impulse, sanitizer). Host tests do NOT establish hardware performance.

**Blocker:** Molecular (https://github.com/nzimas/molecular) documents the main NDS32 application's stereo TX over SPI to the ARM effects processor but has not decoded the reverse SPI RX as live analog-input audio. Until the input path is measured we cannot route the FX chain to the guitar/line input. Molecular's device build needs the external BP10xx SDK and Andes NDS toolchain. Modifying Molecular to build a device application would require GPL-3.0-only compliance.

The former site flasher in `pocket-master/` must remain untouched. v0.7 was the hardware-verified custom baseline, v0.8 DST/AMP was silent, v0.9 is experimental, and later reconnect/GUI revisions are not by themselves evidence of a stable audio path. Preserve official V1.3.3 recovery.

Next: read-only SPIS RX capture under official V1.3.3 companion ARM firmware, then link and benchmark input FX on device only if true audio samples are established. This branch is intentionally a draft, not an update announcement.

Source authored for this prototype: MIT (see LICENSE).

## Passive SPIS diagnostic module
`src/spis_probe.{h,c}` adds a bounded, nonblocking single-producer/single-consumer queue for completed 512-byte SPI RX DMA frames. It copies one of every 32 frames into a four-slot ring, then foreground code may drain records for host analysis. No USB activity inside the ISR; no audio-path memory allocation. Host tests verify decimation, FIFO and overflow; the actual ISR hook and USB export remain to be measured and integrated on hardware. This still does **not** prove that RX contains usable analog audio.
