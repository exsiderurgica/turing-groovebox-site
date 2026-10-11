from pathlib import Path
import argparse, hashlib, json, io
from PIL import Image, ImageDraw, ImageFont

OFFICIAL_SHA='d9112f12a37e3731e540b325d2b9788f005de7f640c129ac30465ea1dc57e7a3'
DESC=0x38; DSZ=16; BASE_RUNTIME=0x10000
PAGE0_AUTO_TARGET_OFF=0x13da6
PAGE0_AUTO_TARGET_OLD=b'\x84\x0e'
PAGE0_AUTO_TARGET_NEW=b'\x84\x02'

def crc16_modbus(buf):
    crc=0xffff
    for x in buf:
        crc ^= x
        for _ in range(8): crc=((crc>>1)^0xA001) if crc&1 else crc>>1
    return crc & 0xffff

def find_region(raw,rid):
    n=int.from_bytes(raw[0x22:0x24],'little'); base=DESC+n*DSZ
    for i in range(n):
        e=DESC+i*DSZ
        if chr(raw[e+3])==rid:
            off=base+int.from_bytes(raw[e+8:e+12],'little'); sz=int.from_bytes(raw[e+12:e+16],'little')
            return e,off,sz
    raise RuntimeError('region '+rid+' missing')

def replace_once(buf,old,new,label):
    if len(old)!=len(new): raise RuntimeError(label+' length mismatch')
    hits=[]; p=0
    while True:
        q=buf.find(old,p)
        if q<0: break
        hits.append(q); p=q+1
    if len(hits)!=1: raise RuntimeError(f'{label}: expected one hit got {hits}')
    q=hits[0]; buf[q:q+len(old)]=new; return q

def gif_length(data,off):
    p=off
    if data[p:p+6] not in (b'GIF87a',b'GIF89a'): raise RuntimeError('not gif')
    p+=6; packed=data[p+4]; p+=7
    if packed&0x80: p+=3*(2**((packed&7)+1))
    while p<len(data):
        t=data[p]; p+=1
        if t==0x3b:return p-off
        if t==0x21:
            p+=1
            while True:
                n=data[p]; p+=1
                if n==0: break
                p+=n
        elif t==0x2c:
            packed=data[p+8]; p+=9
            if packed&0x80:p+=3*(2**((packed&7)+1))
            p+=1
            while True:
                n=data[p]; p+=1
                if n==0:break
                p+=n
        else: raise RuntimeError(f'bad gif block {t:02x}')
    raise RuntimeError('gif trailer missing')

def make_splash():
    try: font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',25)
    except Exception: font=ImageFont.load_default()
    try: small=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',9)
    except Exception: small=ImageFont.load_default()
    pal=[0,0,0,255,205,0,255,255,255,70,70,70]+[0,0,0]*(256-4)
    frames=[]
    for i in range(55):
        im=Image.new('P',(148,48),0); im.putpalette(pal); d=ImageDraw.Draw(im)
        title='TTG FX'; bb=d.textbbox((0,0),title,font=font)
        d.text(((148-(bb[2]-bb[0]))//2,3),title,font=font,fill=1)
        sub='EXSIDERURGICA'; bb=d.textbbox((0,0),sub,font=small)
        d.text(((148-(bb[2]-bb[0]))//2,35),sub,font=small,fill=2)
        w=36+((i%20)*3 if i%40<20 else (39-i%40)*3)
        if w<36:w=36
        d.rectangle(((148-w)//2,31,(148+w)//2,32),fill=1)
        frames.append(im)
    out=io.BytesIO(); frames[0].save(out,format='GIF',save_all=True,append_images=frames[1:],duration=40,loop=0,disposal=2,optimize=True)
    return out.getvalue()

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('stock'); ap.add_argument('output'); ap.add_argument('--manifest',required=True)
    for n in ['page2','pre','next','enc-down','enc-up','enc-press','hook']: ap.add_argument('--'+n,required=True)
    a=ap.parse_args(); src=Path(a.stock).read_bytes(); sha=hashlib.sha256(src).hexdigest()
    if sha!=OFFICIAL_SHA: raise SystemExit(f'REFUSE BASE {sha}')
    raw=bytearray(src); be,bs,bn=find_region(raw,'b'); b=bytearray(raw[bs:bs+bn]); patches=[]

    files={'page2':a.page2,'pre':a.pre,'next':a.next,'enc_down':a.enc_down,'enc_up':a.enc_up,'enc_press':a.enc_press}
    sites=[
      ('page2_update',0x235b6,'49fff925','page2'),
      ('pre_page',0x52e48,'4902b8a9','pre'),
      ('next_page',0x52ee4,'4902b84e','next'),
      ('encoder_down',0x53a54,'4900248a','enc_down'),
      ('encoder_up',0x53c88,'490022d8','enc_up'),
      ('encoder_press',0x53cd4,'49001fd8','enc_press'),
    ]
    for name,rt,hx,key in sites:
        off=rt-BASE_RUNTIME; old=bytes.fromhex(hx); new=Path(files[key]).read_bytes()
        if b[off:off+4]!=old: raise SystemExit(f'{name} guard got={b[off:off+4].hex()} expected={hx}')
        if len(new)!=4: raise SystemExit(f'{name} patch len={len(new)}')
        b[off:off+4]=new; patches.append({'name':name,'runtime':hex(rt),'old':hx,'new':new.hex()})

    cave_off=0xff314; cave_end=0xff656; hook=Path(a.hook).read_bytes()
    if any(b[cave_off:cave_end]): raise SystemExit('verified cave no longer zero')
    if len(hook)>cave_end-cave_off: raise SystemExit(f'hook too large {len(hook)}')
    b[cave_off:cave_off+len(hook)]=hook
    patches.append({'name':'new_nds32_ui_state_machine','runtime':'0x10f314','length':len(hook)})

    # Boot directly to page2 after TTG splash. Page2 is immediately repurposed by our renderer hook.
    if b[PAGE0_AUTO_TARGET_OFF:PAGE0_AUTO_TARGET_OFF+2]!=PAGE0_AUTO_TARGET_OLD: raise SystemExit('page0 direct-boot guard failed')
    b[PAGE0_AUTO_TARGET_OFF:PAGE0_AUTO_TARGET_OFF+2]=PAGE0_AUTO_TARGET_NEW
    patches.append({'name':'direct_boot_to_TTG_chain','runtime':'0x23da6','old':'840e','new':'8402'})

    old=b'NR\0\0FX1\0DRV\0AMP\0IR\0\0EQ\0\0FX2\0DLY\0RVB\0'
    new=b'IN\0\0D1\0\0F1\0\0F2\0\0D2\0\0DLY\0CLD\0OUT\0SYS\0'
    pos=replace_once(b,old,new,'slot captions'); patches.append({'name':'chain_slot_labels','b_offset':hex(pos)})
    try:
        pos=replace_once(b,b'Effects\0',b'TTG FX\0\0','Effects title'); patches.append({'name':'TTG_title','b_offset':hex(pos)})
    except Exception as e: raise
    pos=replace_once(b,b'Preset VOL\0',b'MASTER VOL\0','Master volume title'); patches.append({'name':'master_volume_title','b_offset':hex(pos)})

    hits=[];p=0
    while True:
        q=b.find(b'GIF89a',p)
        if q<0:break
        hits.append(q);p=q+1
    if len(hits)!=1: raise SystemExit(f'GIF hits {hits}')
    goff=hits[0]; slot=gif_length(b,goff); gif=make_splash()
    if len(gif)>slot: raise SystemExit(f'splash {len(gif)} > slot {slot}')
    b[goff:goff+slot]=gif+b'\0'*(slot-len(gif)); patches.append({'name':'generated_TTG_splash','b_offset':hex(goff),'slot':slot,'gif':len(gif),'frames':55})

    raw[bs:bs+bn]=b; crc=crc16_modbus(b); raw[be]=(crc>>8)&255; raw[be+1]=crc&255
    regs=[]
    for rid in 'cgfbe':
        e,s,n=find_region(raw,rid); calc=crc16_modbus(raw[s:s+n]); stored=(raw[e]<<8)|raw[e+1]
        if calc!=stored: raise SystemExit(f'CRC FAIL {rid}')
        regs.append({'id':rid,'size':n,'crc16':f'{calc:04x}'})
    for rid in 'cgfe':
        _,s,n=find_region(raw,rid); _,ss,nn=find_region(src,rid)
        if n!=nn or raw[s:s+n]!=src[ss:ss+nn]: raise SystemExit(f'ISOLATION FAIL {rid}')
    if raw[4:8]!=src[4:8] or len(raw)!=len(src): raise SystemExit('container invariant failed')

    Path(a.output).write_bytes(raw); outsha=hashlib.sha256(raw).hexdigest(); diff=sum(x!=y for x,y in zip(src,raw))
    manifest={
      'name':'Pocket TTG FX DIRECT CHAIN M1 v0.2','base_sha256':OFFICIAL_SHA,'output_sha256':outsha,'size':len(raw),
      'changed_regions':['b'],'changed_bytes':diff,'regions':regs,'patches':patches,'hook_bytes':len(hook),
      'boot':'stock hardware/runtime init -> generated TTG FX splash -> direct page2 -> TTG chain renderer',
      'pages':['DRIVE 1','FILTER 1','FILTER 2','DRIVE 2','DELAY','CLOUD'],
      'navigation':{'PRE':'previous page','NEXT':'next page','VALUE_VOL_rotate':'select parameter / edit value','VALUE_VOL_press':'enter edit / toggle discrete'},
      'parameter_shell':{'D1':['DRIVE','COLOR','LEVEL','BYPASS'],'F1':['IN LVL','CUTOFF','RESO','DRIVE','MODE','BYPASS'],'F2':['IN LVL','CUTOFF','RESO','DRIVE','MODE','BYPASS'],'D2':['DRIVE','TONE','LEVEL','BYPASS'],'DLY':['MIX','TIME','FEEDBK','COLOR','BYPASS'],'CLOUD':['POS','SIZE','DENS','TEXT','PITCH','SPREAD','FEEDBK','REVERB','MIX','FREEZE','BYPASS']},
      'state_ram':'0x2001bb40..0x2001bb85 (70/72 audited bytes)','dsp_note':'this milestone validates final boot/navigation/control shell; DSP values are not yet wired to audio engines',
      'legacy_binary_input':False
    }
    Path(a.manifest).write_text(json.dumps(manifest,indent=2)+'\n'); print(json.dumps(manifest,indent=2))

if __name__=='__main__': main()
