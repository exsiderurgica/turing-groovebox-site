#!/usr/bin/env python3
"""Build a proof-of-concept WebWorker / AudioWorklet split for the FM-1 emulator.
Patch the same pinned upstream build as the MT4 mobile V2.
Only emulator transport changes; the original MT4 firmware is untouched.
"""
from pathlib import Path
import sys

web = Path(sys.argv[1]).resolve()
main_path = web / "main.js"
src_path = web / "worklet.js"
main = main_path.read_text()
worklet = src_path.read_text()

def change(source, before, after, label):
    n = source.count(before)
    if n != 1:
        raise RuntimeError(f"{label}: expected one match, found {n}: {before[:100]!r}")
    return source.replace(before, after, 1)

main = change(main,
    'let ac = null, last = null, bootSeq = 0, booting = 0;',
    'let ac = null, last = null, bootSeq = 0, booting = 0;\nlet audioHealth = null;',
    "health state")

main = change(main,
    '  await ac.audioWorklet.addModule("worklet.js");\n'
    '  node = new AudioWorkletNode(ac, "fm1", { numberOfInputs: 0, outputChannelCount: [2] });\n'
    '  node.connect(ac.destination);\n'
    '  node.port.onmessage = onWorklet;',
    '''  await ac.audioWorklet.addModule("output-worklet.js");
  const output = new AudioWorkletNode(ac, "fm1-pcm-output", {
    numberOfInputs: 0, outputChannelCount: [2],
  });
  const engine = new Worker("engine-worker.js");
  const channel = new MessageChannel();
  output.port.postMessage({ type: "connect", port: channel.port1 }, [channel.port1]);
  engine.postMessage({ type: "connect", port: channel.port2 }, [channel.port2]);
  output.port.onmessage = onWorklet;
  engine.onmessage = onWorklet;
  node = { port: engine, disconnect() { output.disconnect(); engine.terminate(); } };
  output.connect(ac.destination);''',
    "start worker audio")

main = change(main,
    '  const m = e.data;\n  if (m.type === "error") {',
    '''  const m = e.data;
  if (m.type === "audio-health") {
    audioHealth = m;
    return;
  }
  if (m.type === "error") {''',
    "audio telemetry")

main = change(main,
    '      "worst audio quantum breakdown: " + (this.peakInfo ? JSON.stringify(this.peakInfo) : "not yet recorded"),',
    '''      "worst worker render chunk: " + (this.peakInfo ? JSON.stringify(this.peakInfo) : "not yet recorded"),
      "realtime PCM buffer: " + (audioHealth ? JSON.stringify(audioHealth) : "not measured"),''',
    "report telemetry")
main = change(main,
    '  ? parsedJitCap : 2;',
    '  ? parsedJitCap : 16;',
    "worker JIT default")
main_path.write_text(main)

# Reuse the whole original Fm1 processor in a non-realtime Worker, preserving
# its boot, save, MIDI, input, screen and CPU behavior.
shim = '''// AudioWorkletProcessor compatibility shim: runs in a dedicated Web Worker.
const sampleRate = 44100;
class AudioWorkletProcessor {
  constructor() {
    this.port = { onmessage: null, postMessage: (msg, transfers) => self.postMessage(msg, transfers || []) };
  }
}
function registerProcessor() {}  // the class is instantiated below in the Worker
'''

tail = '''
// Dedicated Worker: render in 128-frame pieces like the upstream worklet,
// transfer 512-frame stereo chunks to a trivial realtime PCM output worklet.
const engine = new Fm1();
const CHUNK = 512;
const QUANTUM = 128;
const START_BUFFER = 8192;
const MAX_CREDIT = 16384;
let audioPort = null;
let wanted = 0;
let scheduled = false;
const schedulePort = new MessageChannel();

function schedule() {
  if (scheduled || !audioPort || !engine.x || engine.paused || wanted < CHUNK) return;
  scheduled = true;
  schedulePort.port2.postMessage(0);
}

schedulePort.port1.onmessage = () => {
  scheduled = false;
  if (!audioPort || !engine.x || engine.paused || wanted < CHUNK) return;
  const seq = engine.seq;
  const left = new Float32Array(CHUNK);
  const right = new Float32Array(CHUNK);
  for (let i = 0; i < CHUNK / QUANTUM; i++) {
    if (!engine.x || engine.paused || engine.seq !== seq) return;
    const frame = [[new Float32Array(QUANTUM), new Float32Array(QUANTUM)]];
    engine.process([], frame);
    left.set(frame[0][0], i * QUANTUM);
    right.set(frame[0][1], i * QUANTUM);
  }
  wanted -= CHUNK;
  audioPort.postMessage({ type: "audio", left, right }, [left.buffer, right.buffer]);
  schedule();
};

self.onmessage = async e => {
  const m = e.data;
  if (m.type === "connect") {
    audioPort = m.port;
    audioPort.onmessage = event => {
      if (event.data.type !== "need") return;
      wanted = Math.min(MAX_CREDIT, wanted + Math.max(0, event.data.frames | 0));
      schedule();
    };
    audioPort.start?.();
    return;
  }
  if (m.type === "boot" || m.type === "cancel-boot") {
    wanted = 0;
    audioPort?.postMessage({ type: "reset" });
  }
  await engine.port.onmessage(e);
  if (m.type === "boot" && engine.x && !engine.paused) {
    wanted = START_BUFFER;
    schedule();
  } else if (m.type === "resume" && engine.x && !engine.paused) {
    wanted = START_BUFFER;
    schedule();
  }
};
'''
(web / "engine-worker.js").write_text(shim + worklet + tail)
print("Built WebWorker engine + lightweight PCM output transport.")
'''
