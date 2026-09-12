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
y=wrap(c,'TTG is a performance-oriented generative techno instrument for Android. TTG DEV BETA 3 combines five Turing-style sequencing lanes, synthesis voices, polyrhythm, four synced LFOs, a live mixer, a modular BREAK processor, MIDI I/O, WAV recording and performance-safe synchronized switching.',M,y,W-2*M)
c.setFont('Helvetica-Bold',8); c.drawString(M,y-8,'Platform:'); c.setFont('Helvetica',8); c.drawString(M+44,y-8,'Android 8+')
c.setFont('Helvetica-Bold',8); c.drawString(M+150,y-8,'Current release:'); c.setFont('Helvetica',8); c.drawString(M+238,y-8,'DEV BETA 3 / v0.3.0-beta.1')
c.setFont('Helvetica-Bold',13); c.drawString(M,y-58,'QUICK START')
c.setFont('Helvetica',8)
wrap(c,'1. Open SOUND and set BPM, shuffle and voice parameters. 2. Open TURING to generate and mutate the five musical lanes. 3. Use MIXER for performance levels, FX sends, synced switching and the kick-driven SIDECHAIN macro. 4. Open BREAK when you want to process and slice the performance. 5. Use MIDI for external clock or outgoing notes/CC. 6. Use REC to capture the master output.',M,y-82,W-2*M)
c.showPage()

pages=[
('MIXER / PERFORMANCE','The six mixer buses are Kick+Tom, Hats, Bass, Synth, Chords and Break, each with level and mute. Hats, Bass, Synth, Chords and Break also have independent FX SEND switches. Global macros include pitch, bit crushing, performance SYNC and SIDECHAIN. SIDECHAIN is triggered by the actual kick and ducks every voice except the kick, from exact bypass at zero to deep techno pumping at maximum. Delay provides wet, synced time and feedback; reverb provides wet and decay. Beat Repeat keeps division and mix controls. DROP FX remains a continuous performance macro and TMP stores/restores a temporary performance state.','TTG-DEV-BETA2-Mixer.jpg'),
('SOUND / PERFORMANCE','Set BPM with minus/plus or TAP and adjust shuffle. Kick+Tom includes drive, BF groove, kick pitch and envelope, tom level and pitch. Hat/Perc can switch between offbeat eighths and quarter-note on-beat behavior, keeps the independent 16TH layer, and adds a tempo-synced stereo ping-pong delay time control. Bass adds filter ENV and NOISE, with noise following the bass decay VCA. Synth exposes ENV in place of the former resonance UI control. Chords/Drone keeps oscillator pitches, attack, release, filtering, color, drive and Ring Mod.','TTG-DEV-BETA2-Sound.jpg'),
('TURING / POLYRHYTHM / 4 LFO','Five Turing lanes drive T1 Kick+Tom, T2 Hats, T3 Bass, T4 Synth and T5 Chords. DENS controls activity; VAR changes the pattern; division and STEP define rhythmic behavior. FREEZE holds a lane and NEW regenerates it. Four tempo-synced LFO sections provide waveform, rate, target, destination and amount, including long 1 BAR, 2 BAR and 4 BAR rates. GEN controls global evolution.','TTG-DEV-BETA2-Turing.jpg'),
('BREAK / MODULAR CHAIN','BREAK is a dedicated performance processor. BREAK ACTIVE enables it and MUTE removes it from the active path. NEW and FREEZE control pattern generation. DENS, VAR, STEP and division shape the break. PITCH, ROLL, REVERSE and CHOP are macro functions. Texture mode includes the dedicated GRAIN control for particle/window character. The source and routing section feeds pre level, BF groove, drive, cutoff, resonance, bipolar, output level, delay and reverb controls.','TTG-DEV-BETA2-Break.jpg'),
('MIDI / RECORDING','The MIDI panel exposes independent CLOCK IN, CLOCK OUT, NOTES OUT and CC OUT. Clock In follows an external source; Clock Out sends the internal clock at 24 PPQN. Notes and CC output can be enabled independently. Use RESCAN / CLOSE after connecting or changing a MIDI device. REC records the stereo master output as WAV.','Screenshot_20260910-015644.png')]
for t,body,img in pages:
    title(c,t); c.setFont('Helvetica',8); wrap(c,body,M,H-82,W-2*M); add_image(c,img,170,430,H-120); c.showPage()

title(c,'SYNC, RESET AND FEEDBACK')
c.setFont('Helvetica',8)
y=wrap(c,'When performance SYNC is enabled, the SYNC control remains steadily engaged and a small clock indicator marks the quantization boundary. Controls waiting for a synchronized change use a separate pending outline, so audio quantization and visual ON/OFF state remain distinct.',M,H-82,W-2*M)
y=wrap(c,'REC records the master output as WAV. For recovery from an abnormal state, use HARD RESET to restart the application core. The public project site provides the current signed TTG APK, documentation, optional PayPal support and a GitHub Issues entry point for feedback and bug reports.',M,y-10,W-2*M)
y=wrap(c,'When reporting a problem, include the TTG release version, Android version, phone/tablet model, what you were doing when the problem occurred, and whether the problem is reproducible.',M,y-10,W-2*M)
c.setFont('Helvetica',7); c.drawString(M,54,'TTG DEV BETA 3 / v0.3.0-beta.1 - Exsiderurgica - 2026')
c.save()
print(OUT)
