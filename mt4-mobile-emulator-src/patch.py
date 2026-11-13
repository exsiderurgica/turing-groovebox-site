#!/usr/bin/env python3
"""Patch a pinned upstream fm1-emu web build for isolated Android A/B tests."""
from pathlib import Path
import sys

WEB = Path(sys.argv[1]).resolve()
if not (WEB / "main.js").is_file() or not (WEB / "worklet.js").is_file():
    raise SystemExit("Provide the upstream fm1-emu/web directory")

def edit(filename, replacements):
    path = WEB / filename
    source = path.read_text(encoding="utf-8")
    for before, after in replacements:
        count = source.count(before)
        if count != 1:
            raise SystemExit(f"{filename}: expected one match, got {count}: {before[:90]!r}")
        source = source.replace(before, after, 1)
    path.write_text(source, encoding="utf-8")

edit("main.js", [
    (
        'const params = new URLSearchParams(location.search);',
        '''const params = new URLSearchParams(location.search);
// Default to the site's unmodified MT4 GOLD (local .fwsc uploads still work).
if (!params.has("package") && !params.has("fw")) {
  params.set("package", new URL("../EXS_MT4_v030_P7696_USB_TRS_SYNC_GOLD.fwsc", location.href).href);
  params.set("name", "EXS MT4 P7696 GOLD");
}
// ?mobile=0 disables experimental tuning for an A/B comparison.
const MOBILE_TUNE = params.get("mobile") === "1" ||
  (params.get("mobile") !== "0" && /Android|iPhone|iPad/i.test(navigator.userAgent));'''
    ),
    (
        'let mhz = Number(params.get("mhz") || stored("fm1.mhz") || 100);',
        'let mhz = Number(params.get("mhz") || stored("fm1.mhz") || 240);'
    ),
    (
        'let latency = params.get("latency") || stored("fm1.latency") || "interactive";',
        'let latency = params.get("latency") || stored("fm1.latency") || "playback";'
    ),
    (
        'mhz: clock, jit: params.get("jit") !== "0",',
        'mhz: clock, jit: params.get("jit") !== "0", mobile: MOBILE_TUNE,'
    )
])

edit("worklet.js", [
    (
        '    x.fm1_jit(m.jit === false ? 0 : 1, 16);',
        '''    // Spread JIT compiling over more audio quanta on mobile.
    // It may reduce pauses but prolongs JIT warmup.
    x.fm1_jit(m.jit === false ? 0 : 1, m.mobile ? 4 : 16);'''
    ),
    (
        '    this.mhz = m.mhz;',
        '    this.mobile = Boolean(m.mobile);\n    this.mhz = m.mhz;'
    ),
    (
        '    if (this.reportFrames >= sampleRate / 30) {',
        '''    // Lower LCD snapshot/message transfer rate reduces Android GC pressure.
    if (this.reportFrames >= sampleRate / (this.mobile ? 12 : 30)) {'''
    )
])

edit("index.html", [
    (
        "<title>FM-1 Emulator</title>",
        "<title>EXS MT4 Mobile Emulator - BETA</title>"
    )
])
print("MT4 mobile A/B patch applied: JIT 4/quantum, LCD 12 fps.")


# V2 experiment: configurable JIT budget + identify which stage owns the worst quantum.
edit("main.js", [
    (
        '  (params.get("mobile") !== "0" && /Android|iPhone|iPad/i.test(navigator.userAgent));',
        '''  (params.get("mobile") !== "0" && /Android|iPhone|iPad/i.test(navigator.userAgent));
const parsedJitCap = Number(params.get("jitcap"));
const JIT_CAP = Number.isInteger(parsedJitCap) && parsedJitCap >= 1 && parsedJitCap <= 16
  ? parsedJitCap : 2;'''
    ),
    (
        'mhz: clock, jit: params.get("jit") !== "0", mobile: MOBILE_TUNE,',
        'mhz: clock, jit: params.get("jit") !== "0", mobile: MOBILE_TUNE, jitCap: JIT_CAP,'
    ),
    (
        '    this.stamp = null; this.jit = null; this.dismissed = false;',
        '    this.stamp = null; this.jit = null; this.peakInfo = null; this.dismissed = false;'
    ),
    (
        '    if (m.worst > this.worst) this.worst = m.worst;',
        '''    if (m.worst > this.worst) {
      this.worst = m.worst;
      this.peakInfo = m.peakInfo ?? null;
    }'''
    ),
    (
        '      ' + chr(96) + 'FM-1 emulator report (',
        '''      "MT4 mobile V2: " + (MOBILE_TUNE ? "on" : "off") +
        " · JIT cap: " + (MOBILE_TUNE ? JIT_CAP : 16) + " regions/quantum",
      "worst audio quantum breakdown: " + (this.peakInfo ? JSON.stringify(this.peakInfo) : "not yet recorded"),
      ''' + chr(96) + '''FM-1 emulator report ('''
    )
])

edit("worklet.js", [
    (
        '    x.fm1_jit(m.jit === false ? 0 : 1, m.mobile ? 4 : 16);',
        '    x.fm1_jit(m.jit === false ? 0 : 1, m.mobile ? m.jitCap : 16);'
    ),
    (
        '    this.worst = 0;                    // the longest quantum among them (once audio plays)',
        '''    this.worst = 0;                    // the longest quantum among them (once audio plays)
    this.worstInfo = null;'''
    ),
    (
        '  process(_inputs, outputs) {',
        '''  capturePeak(took, renderMs, jitMs, jitRegions, afterAudioMs) {
    if (!this.playing || took <= this.worst) return;
    this.worst = took;
    this.worstInfo = {
      totalMs: Math.round(took * 10) / 10,
      renderMs: Math.round(renderMs * 10) / 10,
      jitMs: Math.round(jitMs * 10) / 10,
      jitRegions,
      overheadMs: Math.round((took - afterAudioMs) * 10) / 10,
    };
  }

  process(_inputs, outputs) {'''
    ),
    (
        '    const t0 = now();',
        '''    const t0 = now();
    const jitMs0 = this.jit.compileMs;
    const jitRegions0 = this.jit.regions;'''
    ),
    (
        '    const buffer = x.memory.buffer, lp = x.fm1_out_l(), rp = x.fm1_out_r();',
        '''    const renderMs = now() - t0;
    const jitMs = this.jit.compileMs - jitMs0;
    const jitRegions = this.jit.regions - jitRegions0;
    const buffer = x.memory.buffer, lp = x.fm1_out_l(), rp = x.fm1_out_r();'''
    ),
    (
        '    if (out[1]) out[1].set(r);',
        '''    if (out[1]) out[1].set(r);
    const afterAudioMs = now() - t0;'''
    ),
    (
        '        busy: this.lastTiming?.busy ?? 0, worst: this.lastTiming?.worst ?? 0,',
        '        busy: this.lastTiming?.busy ?? 0, worst: this.lastTiming?.worst ?? 0, peakInfo: this.lastTiming?.peakInfo ?? null,'
    ),
    (
        '      const took = now() - t0;\n      // busy:',
        '''      const took = now() - t0;
      this.capturePeak(took, renderMs, jitMs, jitRegions, afterAudioMs);
      // busy:'''
    ),
    (
        'this.lastTiming = { busy: this.busy + took, worst: playing ? Math.max(this.worst, took) : this.worst,',
        'this.lastTiming = { busy: this.busy + took, worst: this.worst, peakInfo: this.worstInfo,'
    ),
    (
        '      this.worst = 0;\n      this.reportFrames = 0;',
        '      this.worst = 0;\n      this.worstInfo = null;\n      this.reportFrames = 0;'
    ),
    (
        '      const took = now() - t0;\n      this.busy += took;\n      if (playing) this.worst = Math.max(this.worst, took);',
        '''      const took = now() - t0;
      this.busy += took;
      this.capturePeak(took, renderMs, jitMs, jitRegions, afterAudioMs);'''
    )
])
print("V2: per-quantum JIT budget and detailed peak-stage diagnostics enabled.")
