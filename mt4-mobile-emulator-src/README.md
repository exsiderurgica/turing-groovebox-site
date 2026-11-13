# MT4 MOBILE EMULATOR - EXPERIMENTAL

Mobile A/B build of Joakim Eriksson's MIT-licensed fm1-emu
(https://github.com/joakimeriksson/fm1-emu), pinned to source commit
3e3fb27df91239fe0e7c5bbf82b14c20ad7dd62a.

No EXS MT4 firmware files, the stable GOLD release, or the flasher are changed.

## Motivation and scope

In a 17-second Android MT4 P7697 user report at 240 MHz, speed fell from
about 2.2x to 0.47x, the worst AudioWorklet quantum was 43 ms (2.9 ms nominal
deadline), the worklet had compiled 2,794 JIT regions / 660 ms in aggregate,
and 5 short audio quanta were observed. This does not prove JIT or UI updates
caused the performance collapse.

On Android, the mobile=1 default makes two deliberately small tweaks:
- Limits JIT code-region compilation to 4 per worklet quantum (stock: 16).
- Refreshes LCD data and LED state about 12 fps (stock: 30 fps).

This may reduce per-quantum spikes and allocation pressure at the cost of
slower JIT warmup and reduced display frame rate. Audio synthesis and
the firmware are unchanged.

Default is MT4 P7696 GOLD, 240 MHz, playback audio buffer.
Use the Open .fwsc button for another build, including P7697.

## Same-device A/B test

On the published test route, compare ?mobile=1 and ?mobile=0 with the *same*
firmware, clock, patch, and phone. Run each for at least two minutes and copy
the performance report after warmup. Check worst quantum, short audio
quanta, load history, real-time speed and touch/encoder responsiveness.
The beta is not claimed to resolve the issue until this test passes.

Build workflow reconstructs this mobile variant from pinned MIT upstream
source and its own matching WebAssembly binary, then publishes only to the
isolated mt4/emulator-mobile/ staging path.

## V2 diagnostic beta

The first Android A/B report did not improve real-time behavior: P7696 on the site had a 214% worst-second load, 35 ms worst quantum, 0.47x minimum speed, and five short quanta. The earlier P7697 report had 211%, 43 ms, 0.47x and five short quanta; because firmware builds differ, this comparison is only suggestive.

V2 adds a narrower configurable JIT compilation budget (default 2 regions/quantum when mobile tuning is on; query parameter jitcap=1..16) and instrumentation for the worst audio quantum: total time, render time, JIT compilation time and region count, and post-audio overhead. The Copy a report output includes these details along with the tuning state. Run the same firmware and clock across both URLs. A lower JIT cap may also lengthen warm-up, so do not interpret these settings as a verified fix.

## V3 Worker-based audio (experimental)

V3 attempts to address the 120 ms audio render stalls recorded on the Nothing Phone
with MT4 GOLD at 240 MHz. The emulator and JIT still run the **unmodified .fwsc**,
but outside the browser's realtime AudioWorklet. A dedicated Web Worker runs the
same 128-frame machine quanta and transfers stereo PCM in 512-frame messages to
an AudioWorklet that ONLY outputs samples.

The PCM output starts after a 4,096-frame prefill (~93 ms) and attempts to keep
8,192 frames (~186 ms) prepared. This adds audio/control latency and may mask
short stalls, but **does not make emulation faster** and cannot fix sustained
sub-real-time throughput. Underflows increment a counter; the output waits
for another prefill before resuming audio. The worst worker render chunk and
the output-buffer health are included in Copy a report.

Sources are pinned to upstream fm1-emu commit 3e3fb27 and retain the upstream
MIT license. CI checks JavaScript syntax, tests the PCM queue and builds the
matching WASM. CI is NOT a substitute for mobile Chrome testing; this is an
unverified prototype, **not** a production replacement for MT4 Mobile V2.
The original MT4 stable firmware and integrated flasher are unchanged.
