from pathlib import Path
import os,re,subprocess

base=Path('.github/workflows/build-fm1-backup-readonly.yml')
lines=base.read_text().splitlines(); start=None
for i,l in enumerate(lines):
    if l.strip()=='- name: Generate backup project':
        for j in range(i+1,len(lines)):
            if lines[j].strip()=='run: |': start=j+1; break
        break
if start is None: raise SystemExit('base generator not found')
out=[]
for l in lines[start:]:
    if l.startswith('      - name: Upload APK'): break
    out.append(l[10:] if l.startswith('          ') else l)
script='\n'.join(out)+'\n'; cut=script.find('\ncd "$ROOT"\n')
if cut<0: raise SystemExit('base build tail not found')
Path('/tmp/gen.sh').write_text(script[:cut]+'\n')
subprocess.run(['bash','/tmp/gen.sh'],check=True)
root=Path(os.environ['RUNNER_TEMP'])/'fm1backup'
j=root/'app/src/main/java/com/exsiderurgica/fm1backup/MainActivity.java'
s=j.read_text()
s=s.replace('setProgress(', 'setBarProgress(').replace('progress.setBarProgress(', 'progress.setProgress(')
s=s.replace('void resetRecovery(){ try{c.controlTransfer(0x21,0xFF,0,intf.getId(),null,0,1000);}catch(Exception ignored){} try{c.clearHalt(epIn);}catch(Exception ignored){} try{c.clearHalt(epOut);}catch(Exception ignored){} }','void resetRecovery(){ try{c.controlTransfer(0x21,0xFF,0,intf.getId(),null,0,1000);}catch(Exception ignored){} try{c.controlTransfer(0x02,0x01,0,epIn.getAddress(),null,0,1000);}catch(Exception ignored){} try{c.controlTransfer(0x02,0x01,0,epOut.getAddress(),null,0,1000);}catch(Exception ignored){} }')
pat=re.compile(r'bot\.cmd\(CMD_JUMP,beArgs\(LOADER_ADDR,LOADER_ARG\)\);\s*SystemClock\.sleep\(300\);\s*bot\.resetRecovery\(\);')
s,n=pat.subn('bot.cmd(CMD_JUMP,beArgs(LOADER_ADDR,LOADER_ARG));\n                          log("JUMP ACK ricevuto; LoaderV2 attivo");',s,count=1)
if n!=1: raise SystemExit('JUMP patch failed')
s=s.replace('private static final int FLASH_SIZE = 0x100000, IO = 512;','private static final int FLASH_SIZE = 0x100000, IO = 512;\n              private static final int APP_LO=0x4000, APP_HI=0x93000, APP_SIZE=0x8F000, SECTOR=0x1000, REQ_APP=921;\n              private static final String APP_SHA="58430b00d82d3d5d0243f6f7de053db4e51ebe4f39a5e665fdb7b506bdf081fd";\n              private static final String PRE_SHA="9ad9187521ef6286179dab3c7591f2259a8972d41abb58795cec83965bca7418";\n              private static final String FINAL_SHA="af469c9bf4012216d346ce889ff93b1592362bf7b7180956cded3669f291c739";')
s=s.replace('private static final int CMD_READ_KEY = 0xFC09, CMD_GET_ONLINE_DEVICE = 0xFC0A, CMD_READ_FLASH = 0xFD05;','private static final int CMD_READ_KEY=0xFC09, CMD_GET_ONLINE_DEVICE=0xFC0A, CMD_READ_FLASH=0xFD05;\n              private static final int CMD_ERASE_FLASH_SECTOR=0xFB01, CMD_WRITE_FLASH=0xFB04, CMD_RUN_APP=0xFC0C;')
s=s.replace('private Button enterButton, backupButton, scanButton;','private Button enterButton, backupButton, scanButton, flashButton;\n              private volatile byte[] targetApp;')
s=s.replace('if (granted && dev != null) startBackup(dev);','if (granted && dev != null) startFlash(dev);')
s=s.replace('FM-1 BACKUP READ-ONLY','FM-1 A2.1 DIRECT FLASHER').replace('Nothing → USB-C → FM-1 • nessuna scrittura flash','Nothing → USB-C → FM-1 • UBOOT diretto • backup automatico')
s=s.replace('Questa app può entrare in UBOOT, carica soltanto il loader in RAM e legge 1 MiB di flash. Non cancella e non scrive settori flash.','Seleziona il file APP A2.1 fornito. Prima della scrittura salvo un nuovo backup da 1 MiB, verifico chip e stato iniziale, poi scrivo solo 0x4000-0x92FFF con verifica settore per settore.')
old='backupButton = button("2 · BACKUP FLASH READ-ONLY"); backupButton.setOnClickListener(v -> prepareBackup());\n                  LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); bp2.topMargin=dp(8); root.addView(backupButton,bp2);'
new='backupButton = button("2 · SCEGLI A2.1 APP"); backupButton.setOnClickListener(v -> chooseApp());\n                  LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); bp2.topMargin=dp(8); root.addView(backupButton,bp2);\n                  flashButton=button("3 · SCRIVI A2.1 PERF"); flashButton.setEnabled(false); flashButton.setOnClickListener(v -> confirmFlash());\n                  LinearLayout.LayoutParams bp3 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); bp3.topMargin=dp(8); root.addView(flashButton,bp3);'
if old not in s: raise SystemExit('UI patch target not found')
s=s.replace(old,new)
a=s.index('              private void prepareBackup(){'); b=s.index('              private void startBackup(UsbDevice dev){',a)
pre=r'''              private void chooseApp(){
                  if(busy)return; Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,REQ_APP);
              }
              @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=REQ_APP||result!=RESULT_OK||data==null||data.getData()==null)return;worker.execute(()->{try{byte[] x=readUri(data.getData());String h=sha256(x);log("APP: "+x.length+" B SHA256="+h);if(x.length!=APP_SIZE||!APP_SHA.equals(h))throw new IOException("File APP A2.1 non valido.");targetApp=x;setStatusUi("A2.1 APP verificato. Entra in UBOOT e premi 3 · SCRIVI.",Color.rgb(75,227,122));main.post(()->flashButton.setEnabled(!busy));}catch(Exception e){targetApp=null;log("APP ERROR: "+e);setStatusUi("ERRORE APP: "+e.getMessage(),Color.rgb(255,82,82));main.post(()->flashButton.setEnabled(false));}});}
              private byte[] readUri(Uri u)throws IOException{try(InputStream in=getContentResolver().openInputStream(u)){if(in==null)throw new IOException("Impossibile aprire file.");ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] q=new byte[8192];int n;while((n=in.read(q))>0)o.write(q,0,n);return o.toByteArray();}}
              private void confirmFlash(){if(busy)return;if(targetApp==null){setStatus("Seleziona prima A2.1 APP.",Color.rgb(255,82,82));return;}if(findBootloader()==null){setStatus("UBOOT non visibile. Premi 1 · ENTRA UBOOT.",Color.rgb(255,82,82));return;}new android.app.AlertDialog.Builder(this).setTitle("Scrivere TTG A2.1 PERF?").setMessage("Prima salvo un nuovo backup completo. Scrivo solo l area app, dall alto verso il basso, e verifico ogni settore. NON scollegare USB.").setNegativeButton("ANNULLA",null).setPositiveButton("SCRIVI",(d,w)->prepareFlash()).show();}
              private void prepareFlash(){if(busy)return;UsbDevice d=findBootloader();if(d==null||targetApp==null){setStatus("UBOOT o APP non pronti.",Color.rgb(255,82,82));return;}setBusy(true);if(usbManager.hasPermission(d)){startFlash(d);return;}PendingIntent pi=PendingIntent.getBroadcast(this,0,new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName()),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);usbManager.requestPermission(d,pi);}

'''
s=s[:a]+pre+s[b:]
a=s.index('              private void startBackup(UsbDevice dev){'); b=s.index('              private byte[] readAllRaw()',a)
method=r'''              private void startFlash(UsbDevice dev){
                  worker.execute(()->{UsbDeviceConnection conn=null;Bot bot=null;boolean wrote=false;try{
                      setStatusUi("Apro UBOOT e carico LoaderV2...",Color.YELLOW);setBarProgress(0);conn=usbManager.openDevice(dev);if(conn==null)throw new IOException("Impossibile aprire UBOOT.");bot=new Bot(conn,dev);
                      byte[] iq=bot.xfer(new byte[]{0x12,0,0,0,36,0},null,36,5000);String vendor=ascii(iq,8,8);if(!"WL82".equals(vendor))throw new IOException("UBOOT inatteso: "+vendor);
                      byte[] loader=readAllRaw();if(loader.length!=24064||!sha256(loader).equals("d41da6126760c9d66660bcc0cac8d27d221806c5e369a8036921efe68dca5376"))throw new IOException("Loader RAM non valido.");
                      for(int off=0;off<loader.length;off+=IO){byte[] block=java.util.Arrays.copyOfRange(loader,off,Math.min(loader.length,off+IO));bot.writeBlock(CMD_WRITE_MEMORY,LOADER_ADDR+off,block);}bot.cmd(CMD_JUMP,beArgs(LOADER_ADDR,LOADER_ARG));log("LoaderV2 attivo");
                      byte[] p=bot.cmd(CMD_READ_KEY,u32be(0x00AC6900));byte[] k=new byte[]{p[5],p[4]};crcCipher(k);int key=(k[0]&255)|((k[1]&255)<<8);byte[] q=bot.cmd(CMD_GET_ONLINE_DEVICE,new byte[0]);int typ=q[0]&255;int fid=(q[2]&255)|((q[3]&255)<<8)|((q[4]&255)<<16)|((q[5]&255)<<24);log(String.format(Locale.US,"CHIP key=%04X type=%d flash=%06X",key,typ,fid));if(key!=0x980F||typ!=3||fid!=0x856014)throw new IOException("Identita hardware inattesa.");
                      setStatusUi("Backup pre-flash 1 MiB...",Color.YELLOW);byte[] before=readRange(bot,0,FLASH_SIZE,0,220);String bh=sha256(before);log("PRE SHA256: "+bh);String stamp=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date());saveDownload("fm1-preflash-A2.1-"+stamp+".bin","application/octet-stream",before);if(!PRE_SHA.equals(bh))throw new IOException("Stato iniziale diverso dal backup verificato: scrittura BLOCCATA.");
                      List<Integer> diff=new ArrayList<>();for(int a0=APP_LO;a0<APP_HI;a0+=SECTOR){int bo=a0-APP_LO;boolean same=true;for(int i=0;i<SECTOR;i++)if(before[a0+i]!=targetApp[bo+i]){same=false;break;}if(!same)diff.add(a0);}log("Settori da scrivere: "+diff.size());
                      int done=0;for(int di=diff.size()-1;di>=0;di--){int addr=diff.get(di),src=addr-APP_LO;byte[] sec=java.util.Arrays.copyOfRange(targetApp,src,src+SECTOR);Exception last=null;boolean ok=false;for(int attempt=1;attempt<=3&&!ok;attempt++){try{bot.eraseSector(addr);for(int off=0;off<SECTOR;off+=IO)bot.writeFlash(addr+off,java.util.Arrays.copyOfRange(sec,off,off+IO));ok=java.util.Arrays.equals(sec,bot.readRange(addr,SECTOR));if(!ok)throw new IOException("verify fallita");}catch(Exception e){last=e;log(String.format(Locale.US,"Retry %05X #%d: %s",addr,attempt,e.getMessage()));}}if(!ok)throw new IOException(String.format(Locale.US,"Settore %05X non verificato: %s",addr,last));wrote=true;done++;setBarProgress(220+(int)(580.0*done/Math.max(1,diff.size())));}
                      setStatusUi("Verifica finale 1 MiB...",Color.YELLOW);byte[] fin=readRange(bot,0,FLASH_SIZE,800,980);byte[] exp=before.clone();System.arraycopy(targetApp,0,exp,APP_LO,APP_SIZE);if(!java.util.Arrays.equals(fin,exp))throw new IOException("Verifica finale byte-per-byte fallita.");String fh=sha256(fin);log("FINAL SHA256: "+fh);if(!FINAL_SHA.equals(fh))throw new IOException("Hash finale inatteso.");saveDownload("fm1-A2.1-"+stamp+"-VERIFIED.txt","text/plain",("TTG A2.1 PERF VERIFIED\npre="+bh+"\nfinal="+fh+"\nsectors="+diff.size()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));setBarProgress(1000);setStatusUi("A2.1 PERF SCRITTO E VERIFICATO. Riavvio...",Color.rgb(75,227,122));try{bot.runApp();}catch(Exception ignored){}
                  }catch(Exception e){log("FLASH ERROR: "+e);setStatusUi((wrote?"ERRORE DURANTE SCRITTURA - lascia FM-1 collegato e manda il log: ":"ERRORE PRIMA DELLA SCRITTURA: ")+e.getMessage(),Color.rgb(255,82,82));}finally{try{if(bot!=null)bot.close();}catch(Exception ignored){}try{if(conn!=null)conn.close();}catch(Exception ignored){}setBusy(false);main.post(this::scan);}});
              }
              private byte[] readRange(Bot bot,int addr,int len,int p0,int p1)throws IOException{byte[] out=new byte[len];int done=0;while(done<len){int n=Math.min(IO,len-done);byte[] x=bot.readFlash(addr+done,n);System.arraycopy(x,0,out,done,n);done+=n;if((done&0x3fff)==0||done==len)setBarProgress(p0+(int)((long)(p1-p0)*done/len));}return out;}

'''
s=s[:a]+method+s[b:]
old='byte[] readFlash(int addr,int len) throws IOException { byte[] a=new byte[6]; put32be(a,0,addr); a[4]=(byte)(len>>8); a[5]=(byte)len; return xfer(cdb(CMD_READ_FLASH,a),null,len,5000); }'
new=old+'\n                  byte[] readRange(int addr,int len)throws IOException{byte[] out=new byte[len];int off=0;while(off<len){int n=Math.min(IO,len-off);byte[] x=readFlash(addr+off,n);System.arraycopy(x,0,out,off,n);off+=n;}return out;}\n                  void eraseSector(int addr)throws IOException{if(addr<APP_LO||addr>=APP_HI||(addr&(SECTOR-1))!=0)throw new IOException("Refusing erase");cmd(CMD_ERASE_FLASH_SECTOR,u32be(addr));}\n                  void writeFlash(int addr,byte[] data)throws IOException{if(addr<APP_LO||addr+data.length>APP_HI)throw new IOException("Refusing write");writeBlock(CMD_WRITE_FLASH,addr,data);}\n                  void runApp()throws IOException{cmd(CMD_RUN_APP,u32be(1));}'
if old not in s: raise SystemExit('Bot patch target not found')
s=s.replace(old,new)
s=s.replace('private void setBusy(boolean b){ busy=b; main.post(()->{scanButton.setEnabled(!b);enterButton.setEnabled(!b);backupButton.setEnabled(!b);}); }','private void setBusy(boolean b){busy=b;main.post(()->{scanButton.setEnabled(!b);enterButton.setEnabled(!b);backupButton.setEnabled(!b);if(flashButton!=null)flashButton.setEnabled(!b&&targetApp!=null);});}')
j.write_text(s)
m=root/'app/src/main/AndroidManifest.xml';m.write_text(m.read_text().replace('android:label="FM-1 Backup"','android:label="FM-1 A2.1 Flasher"'))
g=root/'app/build.gradle';g.write_text(g.read_text().replace("versionName '0.1'","versionName '0.1-direct'"))
