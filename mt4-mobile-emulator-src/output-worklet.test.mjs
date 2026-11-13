import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

function make() {
  const events = [];
  let Processor;
  class AudioWorkletProcessor {
    constructor() {
      this.port = {onmessage: null, postMessage: (m) => events.push(m)};
    }
  }
  const source = fs.readFileSync(new URL("./output-worklet.js", import.meta.url), "utf8");
  vm.runInNewContext(source, {
    AudioWorkletProcessor,
    Float32Array,
    registerProcessor: (_name, klass) => { Processor = klass; }
  });
  const node = new Processor();
  const needs = [];
  const port = {
    onmessage: null,
    postMessage: (m) => needs.push(m),
    start() {}
  };
  node.port.onmessage({data: {type: "connect", port}});
  return {node, port, needs, events};
}
function push(port, n, value) {
  port.onmessage({data:{
    type:"audio",
    left: new Float32Array(n).fill(value),
    right: new Float32Array(n).fill(-value)
  }});
}
function play(node) {
  const outputs = [[new Float32Array(128), new Float32Array(128)]];
  assert.equal(node.process([], outputs), true);
  return outputs[0];
}

test("pre-buffers and plays stereo PCM without JIT work in the output callback", () => {
  const {node, port, needs} = make();
  assert.equal(play(node)[0][0], 0);
  for (let i = 0; i < 8; i++) push(port, 512, 0.25);
  let frames = play(node);
  assert.equal(frames[0][0], 0.25);
  assert.equal(frames[1][0], -0.25);
  for (let i = 0; i < 3; i++) play(node);
  assert.deepEqual(needs.map(x => x.type), ["need"]);
  assert.equal(needs[0].frames, 512);
});

test("underflow counts and requests refilling before playback resumes", () => {
  const {node, port, needs} = make();
  for (let i=0;i<8;i++) push(port,512,0.5);
  for (let i=0;i<33;i++) play(node);
  assert.equal(node.underruns,1);
  assert.equal(node.primed,false);
  assert.ok(needs.some(m=>m.type==="need" && m.frames===4096));
  push(port,4096,0.75);
  assert.equal(play(node)[0][0],0.75);
});

test("firmware reset clears buffered audio", () => {
  const {node, port} = make();
  push(port,4096,0.5);
  assert.equal(play(node)[0][0],0.5);
  port.onmessage({data:{type:"reset"}});
  assert.equal(play(node)[0][0],0);
  assert.equal(node.available,0);
});
