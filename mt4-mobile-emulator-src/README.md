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
