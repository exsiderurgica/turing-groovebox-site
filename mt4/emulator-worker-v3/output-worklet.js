// Realtime audio thread: only stereo PCM playback, not CPU emulation or JIT.
const PREFILL = 4096;
const LIMIT = 16384;
class PcmOutput extends AudioWorkletProcessor {
  constructor() {
    super();
    this.source = null;
    this.queue = [];
    this.available = 0;
    this.primed = false;
    this.consumed = 0;
    this.underruns = 0;
    this.quanta = 0;
    this.highWater = 0;
    this.port.onmessage = ({data:m}) => {
      if (m.type !== "connect") return;
      this.source = m.port;
      this.source.onmessage = ({data:p}) => {
        if (p.type === "reset") {
          this.queue = [];
          this.available = 0;
          this.primed = false;
          this.consumed = 0;
          return;
        }
        if (p.type !== "audio" || !p.left || !p.right) return;
        const n = Math.min(p.left.length, p.right.length);
        if (!n || n !== p.left.length || n !== p.right.length) return;
        this.queue.push({ left:p.left, right:p.right, index:0 });
        this.available += n;
        this.highWater = Math.max(this.highWater, this.available);
        while (this.available > LIMIT && this.queue.length > 1) {
          const item = this.queue.shift();
          this.available -= item.left.length - item.index;
        }
      };
      this.source.start?.();
    };
  }
  process(_inputs, outputs) {
    const out = outputs[0], l = out[0], r = out[1], n = l.length;
    if (!this.primed && this.available >= PREFILL) this.primed = true;
    if (this.primed) {
      let p = 0;
      while (p < n && this.queue.length) {
        const item = this.queue[0];
        const count = Math.min(n - p, item.left.length - item.index);
        l.set(item.left.subarray(item.index, item.index + count), p);
        if (r) r.set(item.right.subarray(item.index, item.index + count), p);
        item.index += count;
        this.available -= count;
        p += count;
        if (item.index === item.left.length) this.queue.shift();
      }
      this.consumed += n;
      if (this.consumed >= 512) {
        this.source?.postMessage({ type: "need", frames: this.consumed });
        this.consumed = 0;
      }
      if (p < n) {
        this.underruns++;
        this.primed = false;
        // Re-prime fully after the first underflow instead of staying silent.
        this.source?.postMessage({ type: "need", frames: PREFILL });
      }
    }
    if (++this.quanta % 344 === 0) {
      this.port.postMessage({
        type: "audio-health",
        bufferedFrames: this.available,
        bufferedMs: Math.round(this.available * 1000 / 44100),
        underruns: this.underruns,
        primed: this.primed,
        maxBufferedFrames: this.highWater
      });
    }
    return true;
  }
}
registerProcessor("fm1-pcm-output", PcmOutput);
