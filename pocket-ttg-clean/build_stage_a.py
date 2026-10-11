from pathlib import Path
import argparse, hashlib, struct, json

OFFICIAL_SHA256='d9112f12a37e3731e540b325d2b9788f005de7f640c129ac30465ea1dc57e7a3'
REGION_TABLE=0x38
DESC_SIZE=16

def u16le(b,o): return b[o] | (b[o+1]<<8)
def u32le(b,o): return int.from_bytes(b[o:o+4],'little')

def crc16_modbus(buf):
    crc=0xffff
    for x in buf:
        crc ^= x
        for _ in range(8):
            crc = ((crc>>1)^0xA001) if (crc & 1) else (crc>>1)
    return crc & 0xffff

def find_region(raw, rid):
    count=u16le(raw,0x22)
    payload_base=REGION_TABLE+count*DESC_SIZE
    for i in range(count):
        e=REGION_TABLE+i*DESC_SIZE
        typ=chr(raw[e+3])
        off=u32le(raw,e+8); size=u32le(raw,e+12)
        if typ==rid:
            return e,payload_base+off,size
    raise RuntimeError(f'region {rid} not found')

def replace_once(buf, old, new, label):
    if len(old)!=len(new): raise RuntimeError(f'{label}: replacement length mismatch')
    hits=[]; pos=0
    while True:
        p=buf.find(old,pos)
        if p<0: break
        hits.append(p); pos=p+1
    if len(hits)!=1:
        raise RuntimeError(f'{label}: expected 1 hit, found {len(hits)} at {hits}')
    p=hits[0]
    buf[p:p+len(old)]=new
    return p

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('stock')
    ap.add_argument('output')
    ap.add_argument('--manifest',default=None)
    a=ap.parse_args()
    src=Path(a.stock).read_bytes()
    sha=hashlib.sha256(src).hexdigest()
    if sha != OFFICIAL_SHA256:
        raise SystemExit(f'REFUSING BASE: expected official V1.3.3 {OFFICIAL_SHA256}, got {sha}')
    if src[:4] != b'HTFW': raise SystemExit('not HTFW')
    raw=bytearray(src)
    bdesc,bstart,blen=find_region(raw,'b')
    b=bytearray(raw[bstart:bstart+blen])

    # Clean TTG FX Stage A. Every patch is made directly against official stock.
    slot_old=(b'NR\0\0FX1\0DRV\0AMP\0IR\0\0EQ\0\0FX2\0DLY\0RVB\0')
    slot_new=(b'IN\0\0D1\0\0F1\0\0F2\0\0D2\0\0DLY\0CLD\0OUT\0SYS\0')
    patches=[]
    patches.append(('effect_slots',replace_once(b,slot_old,slot_new,'effect slots'),len(slot_old)))
    patches.append(('effects_title',replace_once(b,b'Effects\0',b'TTG FX\0\0','Effects title'),8))
    patches.append(('preset_volume',replace_once(b,b'Preset VOL\0',b'MASTER VOL\0','Preset VOL'),11))

    # No legacy branding/assets are imported in Stage A.
    raw[bstart:bstart+blen]=b
    new_b_crc=crc16_modbus(b)
    raw[bdesc]=(new_b_crc>>8)&0xff
    raw[bdesc+1]=new_b_crc&0xff

    # Verify all regional CRCs; top-level bytes 0x04..0x07 are deliberately preserved.
    count=u16le(raw,0x22); payload_base=REGION_TABLE+count*DESC_SIZE
    reginfo=[]
    for i in range(count):
        e=REGION_TABLE+i*DESC_SIZE
        rid=chr(raw[e+3]); off=u32le(raw,e+8); size=u32le(raw,e+12)
        start=payload_base+off
        stored=(raw[e]<<8)|raw[e+1]
        calc=crc16_modbus(raw[start:start+size])
        if stored != calc: raise SystemExit(f'CRC FAIL {rid}: stored {stored:04x} calc {calc:04x}')
        reginfo.append({'id':rid,'size':size,'crc16':f'{calc:04x}'})

    # Strong isolation guarantee: c/g/f/e payloads must remain bit-identical to stock.
    for rid in 'cgfe':
        _,s,n=find_region(raw,rid); _,ss,nn=find_region(src,rid)
        if n!=nn or raw[s:s+n]!=src[ss:ss+nn]:
            raise SystemExit(f'ISOLATION FAIL: region {rid} changed')
    if bytes(raw[4:8]) != src[4:8]:
        raise SystemExit('top-level field changed unexpectedly')

    out=Path(a.output); out.write_bytes(raw)
    outsha=hashlib.sha256(raw).hexdigest()
    diff=[i for i,(x,y) in enumerate(zip(src,raw)) if x!=y]
    manifest={
        'name':'Pocket TTG FX CLEAN Stage A',
        'base':'Official Pocket Master V1.3.3',
        'base_sha256':OFFICIAL_SHA256,
        'output_sha256':outsha,
        'size':len(raw),
        'top_level_field_hex':bytes(raw[4:8]).hex(),
        'regions':reginfo,
        'changed_bytes':len(diff),
        'patches':[{'name':n,'b_offset':o,'length':l} for n,o,l in patches],
        'visual_order':['IN','D1','F1','F2','D2','DLY','CLD','OUT','SYS'],
        'note':'Stage A is a clean UI skeleton only. DSP/routing are not yet rewired.'
    }
    mp=Path(a.manifest) if a.manifest else out.with_suffix('.json')
    mp.write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(manifest,indent=2))

if __name__=='__main__': main()
