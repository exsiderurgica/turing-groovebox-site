#!/usr/bin/env python3
"""Publish EXS/MT6 P7710 pages from the archived MT4 website.

Idempotent, fail-closed text/site conversion; actual firmware binaries are
placed separately by the publish workflow after exact SHA-256 verification.
No old binaries, recovery tools or emulators are modified.
"""
from pathlib import Path
import json
import re

ROOT=Path(".")
HASH="fc9c52601f07ed3ecab3ece493b855c3781f8606b3541b65873db74fa846fc6e"
OLD_HASH="e21586d0fcb3921a63693f756867abd77b4ac926f0c22565070dbd94294cceae"
FILE="EXS_MT6_P7710_GOLD.fwsc"
PRIOR="EXS_MT4_P7708_GOLD.fwsc"
VER="P7.7.1.0"
CI="https://github.com/exsiderurgica/EXS-MT4/actions/runs/43760688804"
FINAL="https://exsiderurgica.github.io/turing-groovebox-site/mt6/"
DESC_EN=("EXS/MT6 GOLD: six Macro synth/sample engines, six Turing Machines, "
 "six sequencers (1–16 steps), 12 LFOs, six filters and ADSRs, original TTG "
 "samples and stereo filter BUS. GLO makes varying ambient phrases, editable "
 "decay and clock divisions from 2 to 16. HYBRID splits M1–M3 TECHNO and "
 "M4–M6 AMBIENT. Software CI passed; there is no new physical FM-1 listening "
 "or CPU test report for this release. Bluetooth remains disabled.")
DESC_IT=("EXS/MT6 GOLD: sei Macro synth/sample, sei Turing, sei sequencer "
 "(1–16 step), 12 LFO, sei filtri e ADSR, sample originali TTG e filtro BUS "
 "stereo. GLO genera frasi ambient variabili con decay modificabile e "
 "divisioni da 2 a 16. HYBRID divide M1–M3 TECHNO e M4–M6 AMBIENT. "
 "CI software superata; non risulta un nuovo collaudo fisico FM-1 "
 "di ascolto o CPU per questa versione. Bluetooth disabilitato.")

def get(path): return (ROOT/path).read_text(encoding="utf-8")
def write(path,text):
 p=ROOT/path
 p.parent.mkdir(parents=True,exist_ok=True)
 p.write_text(text,encoding="utf-8")
def exact(text,needle,repl,label):
 n=text.count(needle)
 if n!=1: raise RuntimeError(f"{label}: expected 1 anchor, got {n}: {needle[:90]}")
 return text.replace(needle,repl)
def pattern(text,expression,repl,label):
 out,n=re.subn(expression,lambda _:repl,text,count=0)
 if n!=1: raise RuntimeError(f"{label}: expected 1 regex, got {n}")
 return out
def ptag(en,it,klass=""):
 if '"' in en or '"' in it:raise RuntimeError("unexpected double quote in data attrs")
 return '<p'+(' class="'+klass+'"' if klass else '')+' data-en="'+en+'" data-it="'+it+'">'+en+'</p>'

# Dedicated MT6 landing and documentation pages, preserving original layout.
site=get("mt4/index.html")
site=site.replace("MT4","MT6").replace("mt4","mt6").replace("P7.6.9.7",VER)
site=site.replace("Four Macro voices, eight LFOs, four Turing Machines.",
                  "Six Macro voices, twelve LFOs, six Turing Machines.")
site=site.replace("<h1>EXS / MT6</h1>","<h1>EXS/MT6</h1>")
site=site.replace('font-size="54" class="pixel">MT6</text>',
                  'font-size="40" class="pixel">EXS/MT6</text>')
site=site.replace('font-size="13" class="pixel">EXS</text>',
                  'font-size="13" class="pixel">6 MACROS</text>')
site=site.replace("4× Macro","6× Macro").replace("4× Oscillatori","6× Oscillatori")
site=site.replace("8× LFO","12× LFO")
site=site.replace("4× Turing","6× Turing").replace("4× Macchine Turing","6× Macchine Turing")
site=site.replace("4× Sequencer","6× Sequencer")
site=site.replace("4× Filter","6× Filter").replace("4× Filtri","6× Filtri")
site=site.replace("4× ADSR","6× ADSR")
site=pattern(site,r'<p data-en="MT6 is always fighting[^\n]*</p>',
 ptag("Six voices, 12 LFOs, filters and FX share the FM-1 CPU. Monitor "
      "the CPU meter and disable unused engines or effects if needed.",
      "Sei voci, 12 LFO, filtri ed FX condividono la CPU FM-1. Controlla il "
      "meter e disattiva i motori o gli effetti inutilizzati quando serve."),"CPU")
site=pattern(site,r'<p data-en="Current GOLD: P7\.7\.1\.0\.[^\n]*</p>',
 ptag("Current GOLD: EXS/MT6 P7.7.1.0. Download and verify the "
      "official firmware, then flash with Chrome Android / USB-C / WebMIDI.",
      "GOLD attuale: EXS/MT6 P7.7.1.0. Scarica e verifica il firmware "
      "ufficiale, poi installalo con Chrome Android / USB-C / WebMIDI."),"flash note")
site=pattern(site,r'<p class="small muted" data-en="P7\.7\.1\.0 GOLD:[^\n]*</p>',
 ptag(DESC_EN,DESC_IT,"small muted"),"release notes")
site=pattern(site,r'<p class="small"><a href="\./EXS_MT6_v030_P7696_USB_TRS_SYNC_GOLD\.fwsc"[^\n]*</p>',
 '<p class="small"><a href="./'+PRIOR+'" data-en="P7708 GOLD / rollback" '
 'data-it="P7708 GOLD / ripristino">P7708 GOLD / rollback</a> · '
 '<a href="../mt4/EXS_MT4_v030_P7697_RUNTIME_AUDIT_GOLD.fwsc" '
 'data-en="Older P7697 archive" data-it="Archivio P7697">Older P7697 archive</a></p>',
 "rollback links")
site=pattern(site,r'<p class="small muted" data-en="Four Plaits Macros,[^\n]*</p>',
 ptag("Six Plaits/sample Macros, 12 LFOs, stereo BUS, Clouds Reverb, "
      "Mutable Delay, REGEN and SAVE CONTROL ALL.",
      "Sei Macro Plaits/sample, 12 LFO, BUS stereo, riverbero Clouds, "
      "delay Mutable, REGEN e SAVE CONTROL ALL.","small muted"),
 "six synth features")
site=exact(site,'<div class="spec"><strong data-en="No Presets / No Save" '
 'data-it="Nessun preset / salvataggio">No Presets / No Save</strong></div>',
 '''<div class="spec"><strong data-en="GLO ambient REGEN" data-it="GLO REGEN ambient">GLO ambient REGEN</strong><span data-en="Editable decay / DIV 2–16" data-it="Decay modificabile / DIV 2–16">Editable decay / DIV 2–16</span></div>
<div class="spec"><strong data-en="HYBRID 3+3" data-it="IBRIDA 3+3">HYBRID 3+3</strong><span>3 TECHNO + 3 AMBIENT</span></div>
<div class="spec"><strong data-en="SEQ / TURING 1–16 steps" data-it="SEQ / TURING 1–16 step">SEQ / TURING 1–16 steps</strong></div>
<div class="spec"><strong data-en="Stereo filter BUS" data-it="Filtro BUS stereo">Stereo filter BUS</strong></div>
<div class="spec"><strong data-en="Original TTG samples" data-it="Sample originali TTG">Original TTG samples</strong><span>Tom / Open Hat / Ride</span></div>
<div class="spec"><strong data-en="Mixer / PERFORM / 6 mutes" data-it="Mixer / PERFORM / 6 mute">Mixer / PERFORM / 6 mutes</strong></div>
<div class="spec"><strong data-en="Mutable ping-pong delay" data-it="Delay ping-pong Mutable">Mutable ping-pong delay</strong></div>
<div class="spec"><strong data-en="REC temporary snapshot" data-it="REC snapshot temporaneo">REC temporary snapshot</strong></div>
<div class="spec"><strong data-en="No permanent presets" data-it="Nessun preset permanente">No permanent presets</strong></div>''',"technical specifications")
site=site.replace("MT6 V0.30 / P7.7.1.0 GOLD","EXS/MT6 V0.30 / P7.7.1.0 GOLD")
site=site.replace("14 November 2026","15 November 2026").replace("14 novembre 2026","15 novembre 2026")
site=site.replace("11a1229711bb6573fce3b46e5a298d548a9e67b4376350ccde577c5852e0939d",HASH)
site=site.replace("EXSIDERURGICA / EXS MT6","EXSIDERURGICA / EXS/MT6")
site=site.replace("Four voices","Six voices").replace("four voices","six voices")
for stale in ("4× Macro","4× Turing","4× Sequencer","4× Filter","4× ADSR",
              "8× LFO","P7.6.9.7","EXS_MT6_v030"):
 if stale in site:raise RuntimeError("obsolete front-page claim "+stale)
if "<text x=\"417\"" not in site or ">EXS/MT6</text>" not in site:raise RuntimeError("SVG MT6 title absent")
write("mt6/index.html",site)

manual=get("mt4/manual/index.html")
manual=manual.replace("MT4","MT6").replace("P7.6.9.7",VER).replace("8× LFO","12× LFO").replace("4×","6×")
manual=pattern(manual,r'<p class="small muted" data-en="P7\.7\.1\.0 GOLD:[^\n]*</p>',
 ptag(DESC_EN,DESC_IT,"small muted"),"manual GOLD description")
manual=manual.replace("all four Macro voices","all six Macro voices").replace("sulle quattro Macro","sulle sei Macro")
manual=manual.replace("Four Plaits-based synthesis voices","Six Plaits/sample synthesis voices").replace(
 "Quattro voci di sintesi basate su Plaits","Sei voci di sintesi Plaits/sample")
manual=manual.replace("Two LFOs and a voice envelope for each Macro.",
                      "Two LFOs and an envelope for each of the six Macros.")
manual=pattern(manual,r'<p data-en="AMBIENT creates four evolving musical patterns\.[^\n]*</p>',
 ptag("AMBIENT generates six evolving musical parts. TECHNO focuses on rhythm. "
      "With both modes on, M1–M3 play TECHNO and M4–M6 play AMBIENT. "
      "DRONE is independent and GLO never forces a held gate.",
      "AMBIENT genera sei parti musicali in evoluzione. TECHNO privilegia il "
      "ritmo. Con entrambe attive M1–M3 sono TECHNO e M4–M6 AMBIENT. "
      "DRONE è indipendente e GLO non forza un gate continuo."),"manual hybrid")
manual=pattern(manual,r'<p data-en="Ten musical REGEN categories write notes[^\n]*</p>',
 ptag("Ten REGEN categories generate musical patterns. GLO creates distinct "
      "ambient textures with adjustable decay/ADSR, variable note density "
      "and SEQ divisions 2/3/4/5/6/8/12/16. All six sequencers and Turing "
      "lengths are manually adjustable 1–16 steps, one step at a time.",
      "Dieci categorie REGEN generano pattern musicali. GLO crea texture ambient "
      "diverse con decay/ADSR regolabili, densità delle note variabile e "
      "divisioni SEQ 2/3/4/5/6/8/12/16. I sei sequencer e le sei Turing "
      "sono regolabili a mano da 1 a 16 step, uno per volta."),"manual GLO")
manual=pattern(manual,r'<p data-en="MT6 is in constant tension with the FM1 processor\.[^\n]*</p>',
 ptag("Six Macro engines, 12 LFOs, filters and stereo effects share a limited "
      "CPU budget. Disable heavy effects or inactive engines as needed. "
      "This release has passed software tests, but no new real-FM1 CPU measurements.",
      "Sei Macro, 12 LFO, filtri ed effetti stereo condividono risorse CPU limitate. "
      "Disattiva effetti pesanti o motori inutilizzati quando serve. Questa release "
      "ha superato i test software ma non ha nuove misure CPU sull'FM-1 reale."),"manual CPU")
manual=manual.replace("On the MT6 page, download or prepare the P7.7.1.0 GOLD firmware.",
 "On the MT6 page, download or prepare the EXS/MT6 P7.7.1.0 GOLD firmware.")
manual=manual.replace("Nella pagina MT6, scarica o prepara il firmware P7.7.1.0 GOLD.",
 "Nella pagina MT6, scarica o prepara il firmware EXS/MT6 P7.7.1.0 GOLD.")
manual=manual.replace("32-step","16-step").replace("32 step","16 step")
manual=manual.replace("Four voices","Six voices").replace("four voices","six voices")
manual=manual.replace("Quattro voci","Sei voci").replace("quattro voci","sei voci")
manual=manual.replace("EXSIDERURGICA / MT6 / GOLD","EXSIDERURGICA / EXS/MT6 / GOLD")
for stale in ("4× MACRO","4× TURING","4× SEQUENCER","8× LFO","P7.6.9.7","32-step"):
 if stale in manual:raise RuntimeError("obsolete manual claim "+stale)
write("mt6/manual/index.html",manual)

style=get("mt4/style.css").replace("mt4-counter","mt6-counter")
write("mt6/style.css",style)
sitejs=get("mt4/site.mjs").replace("MT4","MT6").replace("mt4","mt6")
write("mt6/site.mjs",sitejs)
stats=get("mt4/stats.mjs").replace("MT4","MT6").replace(
 "mt4VisitCount","mt6VisitCount").replace("mt4DownloadCount","mt6DownloadCount").replace(
 "mt4-gold-download-started","mt6-gold-download-started").replace("mt4-language-change","mt6-language-change")
# Preserve the existing public counter IDs to retain accumulated history.
stats=stats.replace("exsiderurgica_mt4_visit_counted","exsiderurgica_mt6_visit_counted")
write("mt6/stats.mjs",stats)
flash=get("mt4/flash.mjs").replace("MT4","MT6").replace("mt4","mt6")
flash=flash.replace("P7.6.9.7",VER).replace(
 "11a1229711bb6573fce3b46e5a298d548a9e67b4376350ccde577c5852e0939d",HASH)
flash=flash.replace("EXS_MT6_v030_P7697_RUNTIME_AUDIT_GOLD.fwsc",FILE)
flash=flash.replace("FM-1_997","FM-1_910")
for token in (HASH,FILE,"FM-1_910","compatibleDevice(identity,loaded.meta)","allowUnverifiedFelucca:false"):
 if token not in flash:raise RuntimeError("flash script missing "+token)
for stale in ("FM-1_997","P7.6.9.7","P7697_RUNTIME"):
 if stale in flash:raise RuntimeError("obsolete flash metadata "+stale)
write("mt6/flash.mjs",flash)

# Recovery is still the existing FM1 hardware toolkit; don't rename its
# binaries or falsely imply a new hardware recovery utility.
recovery='''<!doctype html><html lang="it"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>EXS/MT6 / Recovery FM-1</title><link rel="stylesheet" href="../style.css"></head>
<body><div class="wrap"><div class="topbar"><a class="brand" href="../">EXS/MT6</a></div>
<section class="section"><p class="tag">FM-1 / RECOVERY</p><h1>EXS/MT6 / RECOVERY</h1>
<p>Se l'FM-1 è bloccato in modalità WL82 U-BOOT, il flasher WebMIDI non può ripararlo.
Non formattare il dispositivo, non scollegarlo durante una scrittura e conserva un
backup funzionante. I seguenti kit di diagnosi FM-1 sono storici e non specifici di P7710.</p>
<div class="actions"><a class="btn" href="../../mt4/recovery/EXS_MT4_Recovery_Windows.zip">KIT WINDOWS</a>
<a class="btn" href="../../mt4/recovery/EXS_MT4_Recovery_Mac.zip">KIT MACOS</a>
<a class="btn" href="../../mt4/recovery/">GUIDA PRECEDENTE</a></div></section>
<footer><a href="../">EXS/MT6 / HOME</a></footer></div></body></html>'''
write("mt6/recovery/index.html",recovery)

# Generic manual .fwsc flasher; old URL remains a redirect.
for name in ("index.html","app.mjs","fwsc-check.mjs","fwsc-check.test.mjs"):
 text=get("mt4-flasher/"+name)
 if name in ("index.html","app.mjs","fwsc-check.mjs"):
  text=text.replace("MT4","MT6").replace("mt4","mt6")
  text=text.replace("INSTALLAZIONE SPERIMENTALE MT6","INSTALLAZIONE EXS/MT6")
 write("mt6-flasher/"+name,text)

manifest={
 "schema":1,"flash_enabled":True,"ota_enabled":True,
 "release":"EXS/MT6 v0.30 P7.7.1.0 GOLD","product":"FM-1_910",
 "size":610201,"sha256":HASH,"firmware_url":"../mt6/"+FILE,
 "notes":DESC_EN,"designation":"Owner-designated GOLD on 2026-11-15; CI verified",
 "source_ref":"stable/mt6-p7710-gold","source_recipe":
 "311f54d96080f0522b402c3ad85921dc50fe5c32",
 "ci_url":CI,"fallback_url":"../mt6/"+PRIOR,
 "hardware_validation_reported":False,"bluetooth_enabled":False,
 "previous_stable_ref":"stable/mt4-p7708-gold"
}
for path in ("mt6/release.json","mt6-flasher/release.json","mt4-flasher/release.json"):
 write(path,json.dumps(manifest,indent=2,ensure_ascii=False)+"\n")
root=get("index.html")
root=exact(root,'href="mt4/">EXS MT4 / FM1','href="mt6/">EXS/MT6 / FM1',"home navigation")
write("index.html",root)

# Preserve direct /mt4/... historic firmware archive URLs, while fixing
# the human-facing old landing URLs.
for path,target in (
 ("mt4/index.html","../mt6/"),
 ("mt4/manual/index.html","../../mt6/manual/"),
 ("mt4-flasher/index.html","../mt6-flasher/"),
):
 redirect=('''<!doctype html><html lang="it"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="refresh" content="0;url='''+target+'''">
<title>EXS/MT6 — Exsiderurgica</title></head>
<body style="background:#000;color:#fff;font:18px system-ui;padding:2rem">
<p>EXS/MT6: <a style="color:#fff" href="'''+target+'''">apri il nuovo sito</a>.</p></body></html>''')
 write(path,redirect)

# Preserve historical tests on previous immutable binaries, and make the
# up-to-date verification explicitly about MT6 current GOLD.
verify='''name: Verify published EXS/MT6 P7710 GOLD
on:
  push:
    branches: [main]
    paths:
      - 'mt6/**'
      - 'mt6-flasher/**'
      - 'mt4/index.html'
      - 'mt4/manual/index.html'
      - 'mt4-flasher/release.json'
      - '.github/workflows/verify-mt4-p7685-gold.yml'
  pull_request:
    branches: [main]
  workflow_dispatch:
permissions:
  contents: read
jobs:
  verify:
    runs-on: ubuntu-24.04
    timeout-minutes: 6
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
      - name: Validate published MT6 GOLD and preserved P7708 fallback
        run: |
          set -euo pipefail
          python3 - <<'PY_CHECK'
          import json,hashlib
          from pathlib import Path
          fw=Path('mt6/EXS_MT6_P7710_GOLD.fwsc')
          prev=Path('mt6/EXS_MT4_P7708_GOLD.fwsc')
          assert len(fw.read_bytes())==610201
          assert hashlib.sha256(fw.read_bytes()).hexdigest()=='fc9c52601f07ed3ecab3ece493b855c3781f8606b3541b65873db74fa846fc6e'
          assert len(prev.read_bytes())==610201
          assert hashlib.sha256(prev.read_bytes()).hexdigest()=='e21586d0fcb3921a63693f756867abd77b4ac926f0c22565070dbd94294cceae'
          rel=json.loads(Path('mt6-flasher/release.json').read_text())
          assert rel['product']=='FM-1_910' and rel['sha256']==hashlib.sha256(fw.read_bytes()).hexdigest()
          assert rel['firmware_url']=='../mt6/EXS_MT6_P7710_GOLD.fwsc'
          for page in ('mt6/index.html','mt6/manual/index.html'):
           src=Path(page).read_text()
           assert 'EXS/MT6' in src and 'P7.7.1.0' in src
           assert '12× LFO' in src or '12 LFO' in src
           assert 'P7.6.9.7' not in src and '32-step' not in src
          page=Path('mt6/index.html').read_text()
          assert '<text x="417"' in page and '>EXS/MT6</text>' in page
          assert '6× Macro' in page and '6× Turing' in page and '6× Sequencer' in page
          assert 'id="downloadBtn"' in page and 'id="flashBtn"' in page
          flash=Path('mt6/flash.mjs').read_text()
          assert "const SHA='"+rel['sha256']+"'" in flash
          assert "const NAME='EXS_MT6_P7710_GOLD.fwsc'" in flash
          assert "FM-1_910" in flash and 'allowUnverifiedFelucca:false' in flash
          assert 'href="mt6/">EXS/MT6 / FM1' in Path('index.html').read_text()
          print('PASS: MT6 GOLD, P7708 rollback, bilingual specs and safe OTA')
          PY_CHECK
          node --check mt6/flash.mjs
          node --check mt6/site.mjs
          node --check mt6/stats.mjs
          node --check mt6-flasher/app.mjs
          node --experimental-default-type=module --test mt6-flasher/fwsc-check.test.mjs
'''
write(".github/workflows/verify-mt4-p7685-gold.yml",verify)

# Validation: no obsolete marketing copy remains in CURRENT MT6 pages.
for path in ("mt6/index.html","mt6/manual/index.html","mt6/flash.mjs","mt6-flasher/index.html"):
 src=get(path)
 for stale in ("P7.6.9.7","4× Macro","4× Turing","4× Sequencer","8× LFO","32-step"):
  if stale in src:raise RuntimeError(path+" obsolete text "+stale)
if HASH not in get("mt6/index.html") or HASH not in get("mt6/flash.mjs"):
 raise RuntimeError("new GOLD hash missing on front-end")
print("PASS prepared EXS/MT6 website, bilingual manual, SVG text, current release metadata and redirects")
