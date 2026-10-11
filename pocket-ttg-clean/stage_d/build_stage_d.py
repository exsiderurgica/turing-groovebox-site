from pathlib import Path
import argparse,hashlib,json,struct,io
from PIL import Image,ImageDraw,ImageFont
OFFICIAL_SHA='d9112f12a37e3731e540b325d2b9788f005de7f640c129ac30465ea1dc57e7a3';DESC=0x38;DSZ=16;BASE_RUNTIME=0x10000
D1_HDR=0xD494;D1_CODE=0xD4B8;D1_SLOT=0x930;F1_HDR=0x1A6E4;F1_CODE=0x1A708;F1_RUNTIME_BASE=0x10002800;F1_UPDATE=0x10002A7C;F1_NEXT=0x10002BB0
D2_HDR=0x3DB7C;D2_CODE=0x3DBA0;D2_SLOT=0xFF4;F2_HDR=0x41974;F2_CODE=0x4199C;F2_SLOT=0x1484;CLOUD_HDR=0x50F10;CLOUD_CODE=0x50F34;CLOUD_SLOT=0x130
PAGE0_AUTO_TARGET_OFF=0x13da6;PAGE0_AUTO_TARGET_OLD=b'\x84\x0e';PAGE0_AUTO_TARGET_NEW=b'\x84\x02';P08_ROUTE_FILE=0x1BBE90;TTG_ROUTE=bytes([1,2,6,5,7,8,0,3,4,9])
def crc16(b):
 c=0xffff
 for x in b:
  c^=x
  for _ in range(8):c=((c>>1)^0xA001) if c&1 else c>>1
 return c&0xffff
def reg(raw,rid):
 n=int.from_bytes(raw[0x22:0x24],'little');base=DESC+n*DSZ
 for i in range(n):
  e=DESC+i*DSZ
  if chr(raw[e+3])==rid:
   return e,base+int.from_bytes(raw[e+8:e+12],'little'),int.from_bytes(raw[e+12:e+16],'little')
 raise RuntimeError(rid)
def sym(path,name):
 for line in Path(path).read_text().splitlines():
  p=line.split()
  if len(p)>=3 and p[-1]==name:return int(p[0],16)
 raise RuntimeError(name)
def repl(b,o,n,label):
 if len(o)!=len(n):raise RuntimeError(label+' len')
 hits=[];p=0
 while True:
  q=b.find(o,p)
  if q<0:break
  hits.append(q);p=q+1
 if len(hits)!=1:raise RuntimeError(f'{label} hits={hits}')
 b[hits[0]:hits[0]+len(o)]=n;return hits[0]
def giflen(d,o):
 p=o
 if d[p:p+6] not in (b'GIF87a',b'GIF89a'):raise RuntimeError('gif')
 p+=6;pk=d[p+4];p+=7
 if pk&0x80:p+=3*(2**((pk&7)+1))
 while p<len(d):
  t=d[p];p+=1
  if t==0x3b:return p-o
  if t==0x21:
   p+=1
   while True:
    n=d[p];p+=1
    if n==0:break
    p+=n
  elif t==0x2c:
   pk=d[p+8];p+=9
   if pk&0x80:p+=3*(2**((pk&7)+1))
   p+=1
   while True:
    n=d[p];p+=1
    if n==0:break
    p+=n
  else:raise RuntimeError('gif block')
 raise RuntimeError('gif end')
def splash():
 try:big=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',25);small=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',9)
 except Exception:big=small=ImageFont.load_default()
 pal=[0,0,0,255,255,255,240,190,0,70,70,70]+[0,0,0]*(256-4);fr=[]
 for i in range(45):
  im=Image.new('P',(148,48),0);im.putpalette(pal);d=ImageDraw.Draw(im);t='TTG FX';bb=d.textbbox((0,0),t,font=big);d.text(((148-(bb[2]-bb[0]))//2,3),t,font=big,fill=1);t='EXSIDERURGICA';bb=d.textbbox((0,0),t,font=small);d.text(((148-(bb[2]-bb[0]))//2,35),t,font=small,fill=1);w=18+(i%18)*3;d.rectangle(((148-w)//2,31,(148+w)//2,32),fill=2);fr.append(im)
 o=io.BytesIO();fr[0].save(o,format='GIF',save_all=True,append_images=fr[1:],duration=35,loop=0,disposal=2,optimize=True);return o.getvalue()
def module(raw,h,c,lim,blob,nm,ini,pro,label):
 if len(blob)>lim:raise RuntimeError(f'{label} {len(blob)}/{lim}')
 a=sym(nm,ini);b=sym(nm,pro);raw[h+0x10:h+0x18]=struct.pack('<II',a,b);raw[c:c+lim]=b'\0'*lim;raw[c:c+len(blob)]=blob;return {'name':label,'header':hex(h),'code':hex(c),'bytes':len(blob),'budget':lim,'init':hex(a),'process':hex(b)}
def main():
 ap=argparse.ArgumentParser();ap.add_argument('stock');ap.add_argument('output');ap.add_argument('--manifest',required=True)
 for x in ['d1','d2','f1','f2','cloud']:ap.add_argument('--'+x+'-bin',required=True);ap.add_argument('--'+x+'-nm',required=True)
 for x in ['page2','pre','next','enc-down','enc-up','enc-press','text','rodata']:ap.add_argument('--'+x,required=True)
 a=ap.parse_args();src=Path(a.stock).read_bytes()
 if hashlib.sha256(src).hexdigest()!=OFFICIAL_SHA:raise SystemExit('REFUSE NON-STOCK BASE')
 raw=bytearray(src);patch=[]
 patch.append(module(raw,D1_HDR,D1_CODE,D1_SLOT,Path(a.d1_bin).read_bytes(),a.d1_nm,'ttg_d1_init','ttg_d1_process','D1 custom DSP'))
 f1=Path(a.f1_bin).read_bytes();fs=sym(a.f1_nm,'ttg_f1_bridge');ff=F1_CODE+(F1_UPDATE-F1_RUNTIME_BASE)
 if fs!=0x10002A7D or len(f1)>F1_NEXT-F1_UPDATE or struct.unpack_from('<II',raw,F1_HDR+0x10)!=(0x100028B5,0x10002A7D):raise SystemExit('F1 ABI guard')
 raw[ff:ff+len(f1)]=f1;patch.append({'name':'F1 control bridge','file':hex(ff),'bytes':len(f1),'runtime':hex(fs)})
 patch.append(module(raw,F2_HDR,F2_CODE,F2_SLOT,Path(a.f2_bin).read_bytes(),a.f2_nm,'ttg_filters_init','ttg_filters_process','F2 serial filter DSP'))
 patch.append(module(raw,D2_HDR,D2_CODE,D2_SLOT,Path(a.d2_bin).read_bytes(),a.d2_nm,'ttg_d2_init','ttg_d2_process','D2 custom DSP'))
 patch.append(module(raw,CLOUD_HDR,CLOUD_CODE,CLOUD_SLOT,Path(a.cloud_bin).read_bytes(),a.cloud_nm,'ttg_cloud_init','ttg_cloud_process','CLOUD macro DSP'))
 if raw[P08_ROUTE_FILE:P08_ROUTE_FILE+10]!=bytes(range(10)):raise SystemExit('P08 route guard')
 raw[P08_ROUTE_FILE:P08_ROUTE_FILE+10]=TTG_ROUTE;patch.append({'name':'hidden P08 route','file':hex(P08_ROUTE_FILE),'route':list(TTG_ROUTE)})
 be,bs,bn=reg(raw,'b');b=bytearray(raw[bs:bs+bn]);sites=[('page2',0x235b6,'49fff925','page2'),('PRE',0x52e48,'4902b8a9','pre'),('NEXT',0x52ee4,'4902b84e','next'),('enc_down',0x53a54,'4900248a','enc_down'),('enc_up',0x53c88,'490022d8','enc_up'),('enc_press',0x53cd4,'49001fd8','enc_press')];am={'page2':a.page2,'pre':a.pre,'next':a.next,'enc_down':a.enc_down,'enc_up':a.enc_up,'enc_press':a.enc_press}
 for n,rt,hx,k in sites:
  o=rt-BASE_RUNTIME;old=bytes.fromhex(hx);new=Path(am[k]).read_bytes()
  if b[o:o+4]!=old or len(new)!=4:raise SystemExit(f'{n} guard')
  b[o:o+4]=new;patch.append({'name':n,'runtime':hex(rt),'new':new.hex()})
 text=Path(a.text).read_bytes();ro=Path(a.rodata).read_bytes();t0,t1=0x1001ae,0x100544;r0,r1=0xff314,0xff656
 if any(b[t0:t1]) or any(b[r0:r1]) or len(text)>t1-t0 or len(ro)>r1-r0:raise SystemExit(f'cave guard text={len(text)} ro={len(ro)}')
 b[t0:t0+len(text)]=text;b[r0:r0+len(ro)]=ro;patch+= [{'name':'TTG NDS32 code','runtime':'0x1101ae','bytes':len(text),'budget':t1-t0},{'name':'TTG rodata','runtime':'0x10f314','bytes':len(ro),'budget':r1-r0}]
 if b[PAGE0_AUTO_TARGET_OFF:PAGE0_AUTO_TARGET_OFF+2]!=PAGE0_AUTO_TARGET_OLD:raise SystemExit('direct boot guard')
 b[PAGE0_AUTO_TARGET_OFF:PAGE0_AUTO_TARGET_OFF+2]=PAGE0_AUTO_TARGET_NEW
 old=b'NR\0\0FX1\0DRV\0AMP\0IR\0\0EQ\0\0FX2\0DLY\0RVB\0';new=b'IN\0\0D1\0\0F1\0\0F2\0\0D2\0\0DLY\0CLD\0OUT\0SYS\0';patch.append({'name':'captions','b_offset':hex(repl(b,old,new,'captions'))});patch.append({'name':'title','b_offset':hex(repl(b,b'Effects\0',b'TTG FX\0\0','title'))})
 hs=[];p=0
 while True:
  q=b.find(b'GIF89a',p)
  if q<0:break
  hs.append(q);p=q+1
 if len(hs)!=1:raise SystemExit(f'GIF hits={hs}')
 go=hs[0];sl=giflen(b,go);gi=splash()
 if len(gi)>sl:raise SystemExit('splash too large')
 b[go:go+sl]=gi+b'\0'*(sl-len(gi));raw[bs:bs+bn]=b;patch.append({'name':'generated splash','b_offset':hex(go),'bytes':len(gi),'slot':sl})
 regs=[]
 for rid in 'cgfbe':
  e,s,n=reg(raw,rid);c=crc16(raw[s:s+n]);raw[e]=(c>>8)&255;raw[e+1]=c&255;regs.append({'id':rid,'start':hex(s),'size':n,'crc16':f'{c:04x}'})
 for rid in 'cgfbe':
  e,s,n=reg(raw,rid)
  if crc16(raw[s:s+n])!=((raw[e]<<8)|raw[e+1]):raise SystemExit('CRC '+rid)
 for rid in 'cf':
  _,s,n=reg(raw,rid);_,ss,nn=reg(src,rid)
  if n!=nn or raw[s:s+n]!=src[ss:ss+nn]:raise SystemExit('isolation '+rid)
 if raw[4:8]!=src[4:8] or len(raw)!=len(src):raise SystemExit('container invariant')
 Path(a.output).write_bytes(raw);sha=hashlib.sha256(raw).hexdigest();changed=[]
 for rid in 'cgfbe':
  _,s,n=reg(raw,rid);_,ss,nn=reg(src,rid)
  if raw[s:s+n]!=src[ss:ss+nn]:changed.append(rid)
 m={'name':'Pocket TTG FX CLEAN DIRECT CHAIN M2 v0.3','base_sha256':OFFICIAL_SHA,'output_sha256':sha,'size':len(raw),'changed_regions':changed,'changed_bytes':sum(x!=y for x,y in zip(src,raw)),'regions':regs,'patches':patch,'legacy_binary_input':False,'chain':['D1','F1','F2','D2','DLY','CLOUD'],'internal_route':list(TTG_ROUTE),'controls':{'left':'previous block','right':'next block','encoder_rotate':'select/edit','encoder_press':'edit/toggle; bypass toggles block'},'audio':{'D1':'new source DSP','F1':'new source bridge','F2':'new source serial filter DSP','D2':'new source DSP','DLY':'stock Sweep delay','CLOUD':'new source 4-macro Dream wrapper'},'cloud_limit':'4 real macros MIX/SIZE/TEX/SPREAD + bypass in M2; no fake 10-control page','top_level_0x04_preserved':raw[4:8].hex()};Path(a.manifest).write_text(json.dumps(m,indent=2)+'\n');print(json.dumps(m,indent=2))
if __name__=='__main__':main()
