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
c.setFont('Helvetica-Bold',8); c.drawString(M+150,y-8,'Current release:'); c.setFont('Helvetica',8); c.drawString(M+238,y-8,'DEV BETA 2')
c.setFont('Helvetica-Bold',13); c.drawString(M,y-58,'QUICK START')
c.setFont('Helvetica',8)
wrap(c,'1. Open SOUND and set BPM, shuffle and voice parameters. 2. Open TURING to generate and mutate the four musical lanes. 3. Use MIXER for performance levels and temporary FX. 4. Open BREAK BUS when you want to process and slice the performance. 5. Use MIDI for external clock or outgoing notes/CC. 6. Use REC to capture the master output.',M,y-82,W-2*M)
c.showPage()

pages=[
('MIXER / PERFORMANCE','The six buses are Kick+Tom, Hats, Bass, Synth, Chords and Break, each with level and mute. Global macros provide pitch, bit crushing and DROP BUS. Delay offers time, feedback and sync controls; reverb has source selection. Beat Repeat includes division and mix. DROP FX is a continuous high-pass macro: 0 percent is neutral and 100 percent removes the low frequencies. TMP spans the full row and stores or restores a temporary performance state. START controls transport.','TTG-DEV-BETA2-Mixer.jpg'),
('SOUND / PERFORMANCE','Set BPM with minus/plus or TAP and adjust shuffle. Kick+Tom includes drive, BF groove, kick pitch and envelope, tom level and pitch. Hat/Perc provides 909 offbeat and 16th behavior plus decay, drive, filter, resonance and delay. Bass and Synth expose pitch, decay, color, drive, filter and resonance; Synth also includes envelope amount. Chords/Drone provides oscillator pitches, attack, release, filtering, color, drive and Ring Mod.','TTG-DEV-BETA2-Sound.jpg'),
('TURING / POLYRHYTHM / 4 LFO','Four Turing lanes drive T1 Kick+Tom, T2 Bass, T3 Synth and T4 Chords. DENS controls activity; VAR changes the pattern; division and STEP define rhythmic behavior. FREEZE holds a lane and NEW regenerates it. Four tempo-synced LFO sections each provide waveform, rate, target, destination and amount. GEN controls global evolution. REC captures the master output and MIDI opens the routing panel.','TTG-DEV-BETA2-Turing.jpg'),
('BREAK / MODULAR CHAIN','BREAK is a dedicated performance processor. BREAK ACTIVE enables it and MUTE removes it from the active path. NEW and FREEZE control pattern generation. DENS, VAR, STEP and division shape the break. PITCH, ROLL, REVERSE and CHOP are macro functions. The source and routing section feeds pre level, BF groove, drive, cutoff, resonance, bipolar, output level, delay and reverb controls.','TTG-DEV-BETA2-Break.jpg'),
('MIDI / RECORDING','The MIDI panel exposes independent CLOCK IN, CLOCK OUT, NOTES OUT and CC OUT. Clock In follows an external source; Clock Out sends the internal clock at 24 PPQN. Notes and CC output can be enabled independently. Use RESCAN / CLOSE after connecting or changing a MIDI device. REC records the master output as WAV.','Screenshot_20260910-015644.png')]
for t,body,img in pages:
    title(c,t); c.setFont('Helvetica',8); wrap(c,body,M,H-82,W-2*M); add_image(c,img,170,430,H-120); c.showPage()

title(c,'RECORDING, RESET AND FEEDBACK')
c.setFont('Helvetica',8)
y=wrap(c,'REC records the master output as WAV. For recovery from an abnormal state, use HARD RESET to restart the application core. The public project site provides the current TTG APK, documentation, optional PayPal support and a GitHub Issues entry point for feedback and bug reports.',M,H-82,W-2*M)
y=wrap(c,'When reporting a problem, include the TTG release version, Android version, phone/tablet model, what you were doing when the problem occurred, and whether the problem is reproducible.',M,y-10,W-2*M)
c.setFont('Helvetica',7); c.drawString(M,54,'TTG DEV BETA 2 - Exsiderurgica - 2026')
c.save()
print(OUT)
