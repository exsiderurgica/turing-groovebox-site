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
