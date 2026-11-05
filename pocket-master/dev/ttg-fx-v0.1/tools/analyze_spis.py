#!/usr/bin/env python3
"""Analyze 512-byte Pocket Master SPIS RX dumps. Does NOT prove they carry input audio."""
import argparse, math, struct
from pathlib import Path

def report(name, records):
    samples=[v for record in records for v in record]
    if not samples: return
    mean=sum(samples)/len(samples)
    rms=math.sqrt(sum((v-mean)**2 for v in samples)/len(samples))
    peak=max(abs(v) for v in samples)
    dif=sum(abs(samples[i]-samples[i-1]) for i in range(1,len(samples))) / max(1,len(samples)-1)
    print(f"{name:22} RMS={rms:9.1f} peak={peak:6d} DC={mean:8.1f} delta={dif:9.1f}")
def main():
    p=argparse.ArgumentParser()
    p.add_argument('raw',type=Path,help='Concatenated 512-byte RX SPI DMA frames')
    a=p.parse_args()
    data=a.raw.read_bytes()
    if len(data)%512:
        p.error('Input must be a multiple of 512 bytes')
    if not data: p.error('Empty capture')
    n=len(data)//512
    print(f'{n} transfers; {n*512} bytes. Interpretations are NOT confirmed as audio.')
    for off in (0,256):
        for ch in (0,1):
            frames=[]
            for i in range(n):
                block=data[i*512+off:i*512+off+256]
                s=struct.unpack('<128h',block)
                frames.append(s[ch::2])
            report(f'half {off//256}, channel {ch}',frames)
    print('Compare captures: disconnected/silent, 1kHz signal, higher input level.')
    print('A changing RMS alone does not prove real-time routability.')
if __name__=='__main__':main()
