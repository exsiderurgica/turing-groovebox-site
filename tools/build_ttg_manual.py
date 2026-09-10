from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase.pdfmetrics import stringWidth
import os

OUT='TTG-Manual.pdf'
W,H=A4
M=54

def wrap(c,text,x,y,width,font='Helvetica',size=8,leading=11):
    words=text.split(); line=''; yy=y
    for word in words:
        test=(line+' '+word).strip()
        if stringWidth(test,font,size)<=width:
            line=test
        else:
            c.drawString(x,yy,line); yy-=leading; line=word
    if line: c.drawString(x,yy,line); yy-=leading
    return yy

def title(c,t):
    c.setFont('Helvetica-Bold',13); c.drawString(M,H-60,t)

def add_image(c,path,max_w=170,max_h=400,y_top=None):
    if not os.path.exists(path): return
    im=ImageReader(path); iw,ih=im.getSize(); s=min(max_w/iw,max_h/ih)
    w,h=iw*s,ih*s
    x=(W-w)/2
    y=(y_top if y_top is not None else H-130)-h
    c.drawImage(im,x,y,width=w,height=h,preserveAspectRatio=True,mask='auto')

c=canvas.Canvas(OUT,pagesize=A4)
c.setTitle('TTG - User Manual')
c.setAuthor('Exsiderurgica')

# cover / quick start
c.setFont('Helvetica-Bold',22); c.drawString(M,H-64,'TECHNO TURING GROOVEBOX')
c.setFont('Helvetica-Bold',13); c.drawString(M,H-94,'TTG - USER MANUAL')
c.setFont('Helvetica',8); c.drawString(M,H-118,'Exsiderurgica')
y=H-166
c.setFont('Helvetica',8)
y=wrap(c,'TTG is a performance-oriented generative techno instrument for Android. TTG combines four Turing-style sequencing lanes, synthesis voices, polyrhythm, modulation, a live mixer, a modular BREAK processor, MIDI I/O and WAV recording.',M,y,W-2*M)
c.setFont('Helvetica-Bold',8); c.drawString(M,y-8,'Platform:'); c.setFont('Helvetica',8); c.drawString(M+44,y-8,'Android 8+')
c.setFont('Helvetica-Bold',8); c.drawString(M+150,y-8,'Current release:'); c.setFont('Helvetica',8); c.drawString(M+238,y-8,'R16 stable')
c.setFont('Helvetica-Bold',13); c.drawString(M,y-58,'QUICK START')
c.setFont('Helvetica',8)
wrap(c,'1. Open SOUND and set BPM, shuffle and voice parameters. 2. Open TURING to generate and mutate the four musical lanes. 3. Use MIXER for performance levels and temporary FX. 4. Open BREAK BUS when you want to process and slice the performance. 5. Use MIDI for external clock or outgoing notes/CC. 6. Use REC to capture the master output.',M,y-82,W-2*M)
c.showPage()

pages=[
('MIXER / PERFORMANCE','KICK, TOM, HATS, BASS, SYNTH and CHORDS have independent performance levels and mute controls. The FX / TEMP PERFORMANCE section provides delay, reverb, beat repeat and temporary/drop effects. START controls transport. The mixer is designed as the main live-performance surface.','Screenshot_20260910-015633.png'),
('TURING / POLYRHYTHM / MODULATION','Four Turing-style lanes drive T1 Kick + Tom, T2 Bass, T3 Synth and T4 Chords. DENS controls activity/density; VAR changes variation; the rhythmic division and STEP controls define each lane behavior. FREEZE holds a lane and NEW regenerates it. Pitch LFO and Decay LFO add modulation. The bottom length selectors (4/8/16/32/FREE) and GEN control shape global evolution. HARD RESET restarts the application core when a full reset is required.','Screenshot_20260910-014315.png'),
('SOUND / PERFORMANCE','Set the master BPM with minus/plus or TAP and adjust SHUFFLE. Kick + Tom includes drive, groove and pitch-envelope controls. Hat/Perc provides 909/16th-hat behavior, decay and drive. Bass and Synth expose pitch, decay, color, drive and filter controls. Chords/Drone provides performance drive, color, drive amount and chord reverb.','Screenshot_20260910-015644.png'),
('BREAK / MODULAR CHAIN','BREAK is a dedicated performance processor. BREAK ACTIVE enables the processor; MUTE removes it from the active path. NEW and FREEZE control break generation. DENS, VAR and LEN shape the break pattern. PITCH, ROLL, REV and CHOP are macro performance functions with amount and division controls. The modular signal chain runs through Source Insert, Level In, Bipolar A, LP/HP Filter, Resonance, Bipolar B, Drive and Level Out, followed by BPM-locked delay.','Screenshot_20260910-015638.png'),
('MIDI','The MIDI panel exposes independent CLOCK IN, CLOCK OUT, NOTES OUT and CC OUT. Clock In follows an external BPM. Clock Out sends the internal clock at 24 PPQN. Notes and CC output can be enabled independently. Use RESCAN / CLOSE after connecting or changing a MIDI device.','Screenshot_20260910-015635.png')]
for t,body,img in pages:
    title(c,t); c.setFont('Helvetica',8); wrap(c,body,M,H-82,W-2*M); add_image(c,img,170,430,H-120); c.showPage()

title(c,'RECORDING, RESET AND FEEDBACK')
c.setFont('Helvetica',8)
y=wrap(c,'REC records the master output as WAV. For recovery from an abnormal state, use HARD RESET to restart the application core. The public project site provides the current TTG APK, documentation, optional PayPal support and a GitHub Issues entry point for feedback and bug reports.',M,H-82,W-2*M)
y=wrap(c,'When reporting a problem, include the TTG release version, Android version, phone/tablet model, what you were doing when the problem occurred, and whether the problem is reproducible.',M,y-10,W-2*M)
c.setFont('Helvetica',7); c.drawString(M,54,'TTG - Exsiderurgica - 2026')
c.save()
print(OUT)
