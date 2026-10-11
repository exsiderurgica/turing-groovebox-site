from pathlib import Path
import argparse, hashlib, json, io
from PIL import Image, ImageDraw, ImageFont

OFFICIAL_SHA256='d9112f12a37e3731e540b325d2b9788f005de7f640c129ac30465ea1dc57e7a3'
REGION_TABLE=0x38
DESC_SIZE=16

def u16le(b,o): return b[o] | (b[o+1]<<8)
def u32le(b,o): return int.from_bytes(b[o:o+4],'little')
def crc16_modbus(buf):
    crc=0xffff
    for x in buf:
        crc ^= x
        for _ in range(8): crc=((crc>>1)^0xA001) if (crc&1) else (crc>>1)
    return crc & 0xffff

def find_region(raw,rid):
    count=u16le(raw,0x22); base=REGION_TABLE+count*DESC_SIZE
    for i in range(count):
        e=REGION_TABLE+i*DESC_SIZE
        if chr(raw[e+3])==rid:
            return e,base+u32le(raw,e+8),u32le(raw,e+12)
    raise RuntimeError(f'region {rid} not found')

def replace_once(buf,old,new,label):
    if len(old)!=len(new): raise RuntimeError(f'{label}: length mismatch')
    hits=[]; p=0
    while True:
        q=buf.find(old,p)
        if q<0: break
        hits.append(q); p=q+1
    if len(hits)!=1: raise RuntimeError(f'{label}: expected one hit, found {hits}')
    q=hits[0]; buf[q:q+len(old)]=new; return q

def gif_length(data,off):
    p=off
    if data[p:p+6] not in (b'GIF87a',b'GIF89a'): raise RuntimeError('not GIF')
    p+=6; packed=data[p+4]; p+=7
    if packed&0x80: p+=3*(2**((packed&7)+1))
    while p<len(data):
        kind=data[p]; p+=1
        if kind==0x3b: return p-off
        if kind==0x21:
            p+=1
            while True:
                n=data[p]; p+=1
                if n==0: break
                p+=n
        elif kind==0x2c:
            packed=data[p+8]; p+=9
            if packed&0x80: p+=3*(2**((packed&7)+1))
            p+=1
            while True:
                n=data[p]; p+=1
                if n==0: break
                p+=n
        else: raise RuntimeError(f'bad GIF block 0x{kind:02x}')
    raise RuntimeError('GIF trailer not found')

def make_logo_gif():
    font_path='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
    try: font=ImageFont.truetype(font_path,25)
    except Exception: font=ImageFont.load_default()
    palette=[0,0,0, 255,210,0, 255,255,255, 70,70,70] + [0,0,0]*(256-4)
    frames=[]
    for i in range(91):
        im=Image.new('P',(148,48),0); im.putpalette(palette)
        d=ImageDraw.Draw(im); text='TTG FX'
        bb=d.textbbox((0,0),text,font=font); tw=bb[2]-bb[0]; th=bb[3]-bb[1]
        d.text(((148-tw)//2,(48-th)//2-2),text,font=font,fill=1)
        # subtle 91-frame pulse so the stock animation/event timing remains intact
        phase=i%30; w=80+(phase if phase<=15 else 30-phase)*2
        d.rectangle(((148-w)//2,42,(148+w)//2,43),fill=1)
        frames.append(im)
    out=io.BytesIO()
    frames[0].save(out,format='GIF',save_all=True,append_images=frames[1:],duration=40,loop=0,disposal=2,optimize=True)
    data=out.getvalue()
    check=Image.open(io.BytesIO(data))
    if check.size!=(148,48) or getattr(check,'n_frames',0)!=91:
        raise RuntimeError(f'generated splash mismatch size={check.size} frames={getattr(check,"n_frames",0)}')
    return data

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('stock'); ap.add_argument('output'); ap.add_argument('--manifest',default=None)
    a=ap.parse_args(); src=Path(a.stock).read_bytes(); sha=hashlib.sha256(src).hexdigest()
    if sha!=OFFICIAL_SHA256: raise SystemExit(f'REFUSING BASE: expected official V1.3.3 {OFFICIAL_SHA256}, got {sha}')
    if src[:4]!=b'HTFW': raise SystemExit('not HTFW')
    raw=bytearray(src); bdesc,bstart,blen=find_region(raw,'b'); b=bytearray(raw[bstart:bstart+blen]); patches=[]

    # CLEAN shell labels, direct from stock only.
    old=b'NR\0\0FX1\0DRV\0AMP\0IR\0\0EQ\0\0FX2\0DLY\0RVB\0'
    new=b'IN\0\0D1\0\0F1\0\0F2\0\0D2\0\0DLY\0CLD\0OUT\0SYS\0'
    patches.append({'name':'effect_slots','b_offset':replace_once(b,old,new,'effect slots'),'length':len(old)})
    patches.append({'name':'effects_title','b_offset':replace_once(b,b'Effects\0',b'TTG FX\0\0','Effects title'),'length':8})
    patches.append({'name':'preset_volume','b_offset':replace_once(b,b'Preset VOL\0',b'MASTER VOL\0','Preset VOL'),'length':11})

    # Stock splash is exactly one embedded 148x48 animated GIF. Replace only its byte window.
    gif_hits=[]; p=0
    while True:
        q=b.find(b'GIF89a',p)
        if q<0: break
        gif_hits.append(q); p=q+1
    if len(gif_hits)!=1: raise SystemExit(f'expected exactly one GIF89a in region b, got {gif_hits}')
    goff=gif_hits[0]; stock_gif_len=gif_length(b,goff); gif=make_logo_gif()
    if len(gif)>stock_gif_len: raise SystemExit(f'generated splash {len(gif)} > stock slot {stock_gif_len}')
    b[goff:goff+stock_gif_len]=gif+b'\0'*(stock_gif_len-len(gif))
    patches.append({'name':'ttg_fx_splash_gif','b_offset':goff,'slot_length':stock_gif_len,'gif_length':len(gif),'size':'148x48','frames':91,'duration_ms':40})

    raw[bstart:bstart+blen]=b; newcrc=crc16_modbus(b); raw[bdesc]=(newcrc>>8)&255; raw[bdesc+1]=newcrc&255

    # Integrity + isolation. Stage B is region-b only.
    reginfo=[]
    for rid in 'cgfbe':
        e,s,n=find_region(raw,rid); calc=crc16_modbus(raw[s:s+n]); stored=(raw[e]<<8)|raw[e+1]
        if calc!=stored: raise SystemExit(f'CRC FAIL {rid}')
        reginfo.append({'id':rid,'size':n,'crc16':f'{calc:04x}'})
    for rid in 'cgfe':
        _,s,n=find_region(raw,rid); _,ss,nn=find_region(src,rid)
        if n!=nn or raw[s:s+n]!=src[ss:ss+nn]: raise SystemExit(f'ISOLATION FAIL: {rid} changed')
    if raw[4:8]!=src[4:8]: raise SystemExit('top-level field changed unexpectedly')

    out=Path(a.output); out.write_bytes(raw); outsha=hashlib.sha256(raw).hexdigest()
    diff=sum(x!=y for x,y in zip(src,raw))
    manifest={'name':'Pocket TTG FX CLEAN Stage B','base':'Official Pocket Master V1.3.3','base_sha256':OFFICIAL_SHA256,
      'output_sha256':outsha,'size':len(raw),'top_level_field_hex':bytes(raw[4:8]).hex(),'regions':reginfo,
      'changed_bytes':diff,'patches':patches,'boot':'stock runtime -> TTG FX 148x48/91f splash -> stock Effects page renamed TTG FX',
      'visual_order':['IN','D1','F1','F2','D2','DLY','CLD','OUT','SYS'],
      'note':'Stage B contains no legacy TTG binary data. DSP/routing and custom navigation are intentionally not yet changed.'}
    mp=Path(a.manifest) if a.manifest else out.with_suffix('.json'); mp.write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(manifest,indent=2))

if __name__=='__main__': main()
