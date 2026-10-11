from pathlib import Path
import argparse, hashlib, struct, io, json
from PIL import Image, ImageDraw, ImageFont

OFFICIAL_SHA='d9112f12a37e3731e540b325d2b9788f005de7f640c129ac30465ea1dc57e7a3'
DESC=0x38
DSZ=16
BASE_RUNTIME=0x10000


def crc16_modbus(buf):
    crc=0xffff
    for x in buf:
        crc ^= x
        for _ in range(8):
            crc=((crc>>1)^0xA001) if (crc&1) else (crc>>1)
    return crc & 0xffff


def find_region(raw,rid):
    n=int.from_bytes(raw[0x22:0x24],'little'); base=DESC+n*DSZ
    for i in range(n):
        e=DESC+i*DSZ
        if chr(raw[e+3])==rid:
            off=base+int.from_bytes(raw[e+8:e+12],'little')
            size=int.from_bytes(raw[e+12:e+16],'little')
            return e,off,size
    raise RuntimeError('region '+rid+' not found')


def replace_once(buf,old,new,label):
    if len(old)!=len(new): raise RuntimeError(label+' length mismatch')
    hits=[]; p=0
    while True:
        q=buf.find(old,p)
        if q<0: break
        hits.append(q); p=q+1
    if len(hits)!=1: raise RuntimeError(f'{label}: expected one hit, got {hits}')
    q=hits[0]; buf[q:q+len(new)]=new
    return q


def gif_length(data,off):
    p=off
    if data[p:p+6] not in (b'GIF87a',b'GIF89a'): raise RuntimeError('not gif')
    p+=6; packed=data[p+4]; p+=7
    if packed&0x80: p+=3*(2**((packed&7)+1))
    while p<len(data):
        t=data[p]; p+=1
        if t==0x3b: return p-off
        if t==0x21:
            p+=1
            while True:
                n=data[p]; p+=1
                if n==0: break
                p+=n
        elif t==0x2c:
            packed=data[p+8]; p+=9
            if packed&0x80: p+=3*(2**((packed&7)+1))
            p+=1
            while True:
                n=data[p]; p+=1
                if n==0: break
                p+=n
        else: raise RuntimeError(f'bad gif block {t:02x}')
    raise RuntimeError('gif trailer missing')


def make_splash():
    try: font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',25)
    except Exception: font=ImageFont.load_default()
    try: small=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',9)
    except Exception: small=ImageFont.load_default()
    pal=[0,0,0,255,205,0,255,255,255,72,72,72]+[0,0,0]*(256-4)
    frames=[]
    for i in range(43):
        im=Image.new('P',(148,48),0); im.putpalette(pal); d=ImageDraw.Draw(im)
        title='TTG FX'; bb=d.textbbox((0,0),title,font=font)
        d.text(((148-(bb[2]-bb[0]))//2,4),title,font=font,fill=1)
        sub='EXSIDERURGICA'; bb2=d.textbbox((0,0),sub,font=small)
        d.text(((148-(bb2[2]-bb2[0]))//2,35),sub,font=small,fill=2)
        # deterministic small pulse line; no legacy asset bytes are imported
        w=42+(i%11)*2
        d.rectangle(((148-w)//2,31,(148+w)//2,32),fill=1)
        frames.append(im)
    out=io.BytesIO()
    frames[0].save(out,format='GIF',save_all=True,append_images=frames[1:],duration=45,loop=0,disposal=2,optimize=True)
    return out.getvalue()


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('stock'); ap.add_argument('output')
    ap.add_argument('--page2-patch',required=True); ap.add_argument('--pre-patch',required=True); ap.add_argument('--next-patch',required=True); ap.add_argument('--hook',required=True)
    ap.add_argument('--manifest',required=True)
    a=ap.parse_args()
    src=Path(a.stock).read_bytes(); sha=hashlib.sha256(src).hexdigest()
    if sha!=OFFICIAL_SHA: raise SystemExit(f'REFUSE BASE: {sha}')
    raw=bytearray(src); be,bs,bn=find_region(raw,'b'); b=bytearray(raw[bs:bs+bn])

    # Exact stock code-site guards: fail closed if the official image ever changes.
    sites=[
      ('page2_update',0x235b6,bytes.fromhex('49fff925'),Path(a.page2_patch).read_bytes()),
      ('pre_action',   0x52e48,bytes.fromhex('4902b8a9'),Path(a.pre_patch).read_bytes()),
      ('next_action',  0x52ee4,bytes.fromhex('4902b84e'),Path(a.next_patch).read_bytes()),
    ]
    patches=[]
    for name,rt,expected,new in sites:
        off=rt-BASE_RUNTIME
        if b[off:off+4]!=expected: raise SystemExit(f'{name}: stock guard failed got={b[off:off+4].hex()}')
        if len(new)!=4: raise SystemExit(f'{name}: patch len {len(new)}')
        b[off:off+4]=new
        patches.append({'name':name,'runtime':hex(rt),'b_offset':hex(off),'old':expected.hex(),'new':new.hex()})

    # Verified stock all-zero executable cave. Only our newly compiled NDS32 hook enters it.
    cave_off=0xff314; cave_limit=0xff656
    hook=Path(a.hook).read_bytes()
    if any(b[cave_off:cave_limit]): raise SystemExit('code cave is not zero in official stock')
    if len(hook)>(cave_limit-cave_off): raise SystemExit(f'hook too large: {len(hook)}')
    b[cave_off:cave_off+len(hook)]=hook
    patches.append({'name':'new_nds32_shell','runtime':'0x10f314','b_offset':hex(cave_off),'length':len(hook)})

    # Re-label only the stock nine visible main-screen slot captions.
    old=b'NR\0\0FX1\0DRV\0AMP\0IR\0\0EQ\0\0FX2\0DLY\0RVB\0'
    new=b'IN\0\0D1\0\0F1\0\0F2\0\0D2\0\0DLY\0CLD\0OUT\0SYS\0'
    # old and new must remain exactly the same table length
    if len(old)!=len(new): raise SystemExit((len(old),len(new)))
    pos=replace_once(b,old,new,'slot captions')
    patches.append({'name':'slot_captions','b_offset':hex(pos),'length':len(old)})

    # Replace the one stock embedded boot GIF with a newly generated TTG FX asset.
    hits=[]; p=0
    while True:
        q=b.find(b'GIF89a',p)
        if q<0: break
        hits.append(q); p=q+1
    if len(hits)!=1: raise SystemExit(f'expected one GIF89a, got {hits}')
    goff=hits[0]; slot=gif_length(b,goff); gif=make_splash()
    if len(gif)>slot: raise SystemExit(f'new gif {len(gif)} > slot {slot}')
    b[goff:goff+slot]=gif+b'\x00'*(slot-len(gif))
    patches.append({'name':'ttg_fx_splash','b_offset':hex(goff),'slot':slot,'gif_len':len(gif),'frames':43})

    raw[bs:bs+bn]=b
    crc=crc16_modbus(b); raw[be]=(crc>>8)&255; raw[be+1]=crc&255

    # Strong isolation checks: only region b (plus its descriptor CRC bytes) may differ.
    regs=[]
    for rid in 'cgfbe':
        e,s,n=find_region(raw,rid); calc=crc16_modbus(raw[s:s+n]); stored=(raw[e]<<8)|raw[e+1]
        if calc!=stored: raise SystemExit(f'CRC FAIL {rid}: {calc:04x}!={stored:04x}')
        regs.append({'id':rid,'size':n,'crc16':f'{calc:04x}'})
    for rid in 'cgfe':
        _,s,n=find_region(raw,rid); _,ss,nn=find_region(src,rid)
        if n!=nn or raw[s:s+n]!=src[ss:ss+nn]: raise SystemExit(f'ISOLATION FAIL {rid}')
    if raw[4:8]!=src[4:8]: raise SystemExit('top-level field changed')
    if len(raw)!=len(src): raise SystemExit('image length changed')

    Path(a.output).write_bytes(raw)
    outsha=hashlib.sha256(raw).hexdigest()
    manifest={
      'name':'Pocket TTG FX CLEAN INTERACTIVE SHELL v0.1',
      'base':'Official Pocket Master V1.3.3','base_sha256':OFFICIAL_SHA,
      'output_sha256':outsha,'size':len(raw),'regions':regs,'patches':patches,
      'state_ram':'0x2001bb40','state_proof':'stock BSS zero tail GP+0xc100; direct GP refs end at +0xc00f in full static scan',
      'navigation':{'PRE_0x7f36':'previous TTG page','NEXT_0x7f37':'next TTG page'},
      'pages':['DRIVE 1','FILTER 1','FILTER 2','DRIVE 2','DELAY','CLOUD'],
      'ui_method':'stock page2 renderer + existing nested labels group3/child0 and group3/child1; no new GUI widgets',
      'legacy_binary_input':False
    }
    Path(a.manifest).write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(manifest,indent=2))

if __name__=='__main__': main()
