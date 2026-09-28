from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase.pdfmetrics import stringWidth
from reportlab.lib.colors import HexColor
import os

OUT = "TTG-Manual.pdf"
W, H = A4
M = 44
RED = HexColor("#f04d43")
DARK = HexColor("#101114")
GREY = HexColor("#5c6068")
LIGHT = HexColor("#202228")

def wrap_lines(text, font="Helvetica", size=8.2, width=260):
    words = text.split()
    lines, line = [], ""
    for word in words:
        test = (line + " " + word).strip()
        if not line or stringWidth(test, font, size) <= width:
            line = test
        else:
            lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines

def draw_wrapped(c, text, x, y, width, font="Helvetica", size=8.2, leading=11.2, color=None):
    c.setFont(font, size)
    if color:
        c.setFillColor(color)
    for line in wrap_lines(text, font, size, width):
        c.drawString(x, y, line)
        y -= leading
    c.setFillColorRGB(0,0,0)
    return y

def footer(c, page):
    c.setStrokeColor(LIGHT)
    c.line(M, 34, W-M, 34)
    c.setFillColor(GREY)
    c.setFont("Helvetica", 6.7)
    c.drawString(M, 21, "TTG BETA 6 / B126 - EXSIDERURGICA - 2026")
    c.drawRightString(W-M, 21, str(page))
    c.setFillColorRGB(0,0,0)

def page_title(c, kicker, title):
    c.setFillColor(RED)
    c.setFont("Helvetica-Bold", 8)
    c.drawString(M, H-48, kicker.upper())
    c.setFillColorRGB(0,0,0)
    c.setFont("Helvetica-Bold", 17)
    c.drawString(M, H-72, title)

def image_right(c, path, top=H-100, max_w=210, max_h=650):
    if not os.path.exists(path):
        return
    im = ImageReader(path)
    iw, ih = im.getSize()
    s = min(max_w/iw, max_h/ih)
    w, h = iw*s, ih*s
    x = W-M-w
    y = top-h
    c.setStrokeColor(LIGHT)
    c.rect(x-3, y-3, w+6, h+6, stroke=1, fill=0)
    c.drawImage(im, x, y, width=w, height=h, preserveAspectRatio=True, mask="auto")

def feature_page(c, page, kicker, title, intro, sections, image=None):
    page_title(c, kicker, title)
    text_w = 285 if image else W-2*M
    y = H-96
    y = draw_wrapped(c, intro, M, y, text_w, size=8.6, leading=12)
    y -= 10
    for head, body in sections:
        c.setFont("Helvetica-Bold", 9)
        c.setFillColor(RED)
        c.drawString(M, y, head.upper())
        c.setFillColorRGB(0,0,0)
        y -= 13
        y = draw_wrapped(c, body, M, y, text_w, size=7.8, leading=10.4)
        y -= 9
        if y < 68:
            break
    if image:
        image_right(c, image)
    footer(c, page)
    c.showPage()

c = canvas.Canvas(OUT, pagesize=A4)
c.setTitle("TTG Beta 6 - User Manual")
c.setAuthor("Exsiderurgica")

# Cover
c.setFillColor(RED)
c.rect(0, H-16, W, 16, fill=1, stroke=0)
c.setFillColorRGB(0,0,0)
c.setFont("Helvetica-Bold", 27)
c.drawString(M, H-82, "TECHNO TURING")
c.drawString(M, H-114, "GROOVEBOX")
c.setFillColor(RED)
c.setFont("Helvetica-Bold", 17)
c.drawString(M, H-151, "BETA 6 / B126")
c.setFillColorRGB(0,0,0)
c.setFont("Helvetica", 10)
c.drawString(M, H-176, "USER MANUAL")
y = H-225
y = draw_wrapped(c, "TTG is a performance-oriented generative techno instrument for Android. Beta 6 adds sample and loop import, waveform editing, a rhythm-safe Slice Macro, Clouds-based Grain FX, K09/T09 drum models, independent Oneshot pitch/volume, Turing pattern shift and extensive audio/filter stability improvements.", M, y, W-2*M, size=9, leading=13)
y -= 22
c.setFont("Helvetica-Bold", 9)
c.drawString(M, y, "CURRENT RELEASE")
y -= 17
c.setFont("Helvetica", 8.5)
c.drawString(M, y, "TTG Beta 6 / B126")
y -= 14
c.drawString(M, y, "Version 0.6.0-beta.6-b126-stable")
y -= 14
c.drawString(M, y, "Android 8.0+ / API 26+")
y -= 14
c.drawString(M, y, "Target SDK: Android 16 / API 36")
y -= 30
c.setFillColor(RED)
c.setFont("Helvetica-Bold", 10)
c.drawString(M, y, "QUICK START")
c.setFillColorRGB(0,0,0)
y -= 18
for line in [
    "1. SOUND: set tempo and shape Kick+Tom, Hats, Macro A/B and Resonator.",
    "2. TURING: generate five lanes, route CV and shift patterns by one step.",
    "3. MIXER: balance six buses, filter channels and perform with global FX.",
    "4. SAMPLES: import audio, slice loops, edit waveform IN/OUT and use Grain FX.",
    "5. MIDI / OPTIONS: configure clock, Link, projects, USB audio and remote control.",
]:
    y = draw_wrapped(c, line, M, y, W-2*M, size=8.5, leading=12)
    y -= 3
footer(c, 1)
c.showPage()

feature_page(c,2,"02 / SOUND","Sound and voice engines",
"Beta 6 keeps TTG's five-voice performance layout while expanding the kick/tom and direct percussion engines.",
[
("Tempo","BPM, minus/plus, TAP and SHUF control the internal performance clock and swing."),
("Kick + Tom","DRIVE and PUNCH shape the bus. KICK DECAY and TOM DECAY now use the same perceptual curve as Hat decay. K09 and T09 select the alternative kick and tom models. K TUR enables the dedicated Turing behavior."),
("Hat / Perc","4/4/offbeat and 16TH layers are independent. DECAY, DRIVE, OS LEV, MORPH, DELAY and TIME SYNC shape the percussion section."),
("Macro A / B","Two independent macro oscillators provide STEP/FREE pitch, SYNTH/PERC banks, model selection, PITCH, HARMONICS, TIMBRE, MORPH, DECAY and VCA. FM1, FM2 and FM3 are removed from selectable/randomized models."),
("Resonator","Choose model and VOICES, then use PITCH, STRUCTURE, BRIGHTNESS, DAMPING, POSITION and VCA. The extended String + Reverb model remains available.")
],"gallery_src/sound.png")

feature_page(c,3,"03 / MIXER","Mixer and performance FX",
"The Mixer combines six performance buses with channel filtering, global macros and synchronized live FX.",
[
("Six buses","K+T, HAT, MAC A, MAC B, RES and SAMPLES each have level and mute. KICK M mutes the kick only, preserving the tom."),
("Bipolar filters","Hat, Macro A, Macro B, Resonator and Samples use a bipolar LP/BYPASS/HP control with smoothing and de-click improvements."),
("Global macros","PITCH transposes the musical engine, CRUSH applies bit reduction and SYNC quantizes supported performance switches."),
("Performance FX","DELAY, TIME, FDBK, RVB, DECAY and DROP FX are joined by MACKITY, SIDECHAIN, TMP and HARD RESET.")
],"gallery_src/mixer.png")

feature_page(c,4,"04 / TURING","Turing matrix and modulation",
"Five generative lanes drive Kick+Tom, Hats, Macro A, Macro B and Resonator. Beta 6 adds direct pattern rotation without rebuilding the Turing DNA.",
[
("Pattern shift","Use - and + to rotate the active lane one step backward or forward. Tap SHIFT to reset the shift value to zero."),
("Lane controls","DENS, VAR, scale, accent, division, STEP length, ERASE, NEW and FREEZE define each generative lane."),
("Turing CV","Each lane can route its Turing value to supported voice parameters. Pitch stays under TTG's musical sequencing. CV target choices are excluded from randomization."),
("Four LFOs","Each LFO provides waveform, synchronized/free rate, target, destination and AMT. Oneshot pitch destinations are included where applicable."),
("Global controls","ROOT, TUNE and GEN shape the global generative system. REC captures the stereo master WAV.")
],"gallery_src/turing.png")

feature_page(c,5,"05 / SAMPLES","Samples, slicing and Grain FX",
"Beta 6 turns the former BREAK page into a complete sample, slicing and modular processing workspace.",
[
("Sample mode","IMPORT loads audio and PLAY/STOP auditions it. The waveform exposes direct IN/OUT editing, with independent PITCH and VOLUME."),
("Slice mode","LOAD LOOP imports the slicing source. BPM is shown with /2 and x2 corrections. NEW, FREEZE, DENS, VAR, STEP and division define the loop pattern."),
("Rhythm-safe Macro","The B126 MACRO uses only Roll + Chop. No Macro Pitch and no Macro Reverse. The pattern is prepared once; AMT controls how many prepared events are active, avoiding per-step random timing drift."),
("Oneshot / Direct","Closed Hat, Open Hat, Crash and Ride each have independent volume and pitch controls."),
("VCF","The left module is the VCF: BYPASS, LEVEL IN, CUT, RES, PUNCH and HP/LP."),
("Grain FX","The right module is the Clouds-based Grain FX: BYPASS, LEVEL IN, POS, DENS, SIZE, TEXT, PITCH, WET, STEREO, FDBK, REVERB and FREEZE.")
],"gallery/samples-slice.webp")

feature_page(c,6,"06 / SYNC","Clock, MIDI and Ableton Link",
"TTG can run from its internal clock, external MIDI clock or Ableton Link while keeping performance switching on musical boundaries.",
[
("Performance Sync","Mixer SYNC quantizes supported live changes instead of applying them at arbitrary points."),
("MIDI clock","CLOCK IN follows an external clock. CLOCK OUT sends TTG's clock where appropriate. CLOCK OFFSET provides timing correction."),
("Ableton Link","LINK joins a shared session and displays peer status. START/STOP can follow shared transport when enabled."),
("MIDI I/O","MIDI IN/OUT, Notes and CC can be configured independently with device, channel and CC-base controls.")
])

feature_page(c,7,"07 / OPTIONS","Projects, audio routing and remote",
"The MIDI / OPTIONS panel also contains TTG's project and audio-output configuration.",
[
("Projects","SAVE stores a TTG project and LOAD opens it through Android's file browser."),
("System audio","SYS cycles Android system outputs and can select a connected USB DAC when Android exposes it as an output device."),
("USB Direct","USB DIRECT 1/2 is TTG's separate direct-USB stereo path and is independent from SYS output selection."),
("Master split","Each of the six buses can be routed to CH1 and/or CH2 for stereo or dual-mono hardware workflows."),
("Control behavior","CURVE toggles normal/soft-end touch behavior, VALUES controls the parameter overlay and LAT cycles latency profiles."),
("Remote and recording","REMOTE CTRL enables the companion control workflow. REC captures the stereo master output as WAV.")
])

feature_page(c,8,"08 / RELEASE NOTES","What changed in Beta 6",
"Beta 6 / B126 is a major step forward from the public Beta 5 / B93 release.",
[
("Samples","Sample import, waveform IN/OUT editing, loop import with BPM /2 and x2, dedicated sample volume/pitch and the rewritten Samples page."),
("Grain FX","Clouds-based granular processing with routing, independent bypass, freeze, stereo, feedback and reverb."),
("Rhythm","Per-lane Turing shift and a deterministic Slice Macro designed to remain rhythm-safe."),
("Drums","K09/T09 models, independent Oneshot volume/pitch and improved decay behavior."),
("Stability","Multiple de-click, filter smoothing, mute/stop, envelope and Samples-path fixes accumulated across the B94-B126 development cycle."),
("Download","The current signed APK is TTG-BETA6.apk. The online manual and this PDF describe the same B126 stable build.")
])

c.save()
print(OUT)
