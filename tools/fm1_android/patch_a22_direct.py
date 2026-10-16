from pathlib import Path

p = Path('tools/fm1_android/build_direct.py')
s = p.read_text()

repls = {
    '58430b00d82d3d5d0243f6f7de053db4e51ebe4f39a5e665fdb7b506bdf081fd': 'beb82556d6528d71f733b1d8ca2d7e282932488777f1a8e73fddccab276e5cfe',
    '9ad9187521ef6286179dab3c7591f2259a8972d41abb58795cec83965bca7418': 'af469c9bf4012216d346ce889ff93b1592362bf7b7180956cded3669f291c739',
    'af469c9bf4012216d346ce889ff93b1592362bf7b7180956cded3669f291c739': 'bffc26679e36a4384ed0a301cd4c9e8de601074ee80ffd8e0a2400ca6477f9f0',
}
# The PRE and FINAL hashes overlap sequentially, so replace the whole constants
# after doing APP_SHA rather than applying the hash dict blindly.
old = ('private static final int FLASH_SIZE = 0x100000, IO = 512;','private static final int FLASH_SIZE = 0x100000, IO = 512;')

if '58430b00d82d3d5d0243f6f7de053db4e51ebe4f39a5e665fdb7b506bdf081fd' not in s:
    raise SystemExit('A2.1 APP_SHA anchor missing')
s = s.replace('58430b00d82d3d5d0243f6f7de053db4e51ebe4f39a5e665fdb7b506bdf081fd',
              'beb82556d6528d71f733b1d8ca2d7e282932488777f1a8e73fddccab276e5cfe', 1)

old_pair = ('private static final String PRE_SHA=\"9ad9187521ef6286179dab3c7591f2259a8972d41abb58795cec83965bca7418\";\\n'
            '              private static final String FINAL_SHA=\"af469c9bf4012216d346ce889ff93b1592362bf7b7180956cded3669f291c739\";')
new_pair = ('private static final String PRE_SHA=\"af469c9bf4012216d346ce889ff93b1592362bf7b7180956cded3669f291c739\";\\n'
            '              private static final String FINAL_SHA=\"bffc26679e36a4384ed0a301cd4c9e8de601074ee80ffd8e0a2400ca6477f9f0\";')
if old_pair not in s:
    raise SystemExit('A2.1 PRE/FINAL hash anchors missing')
s = s.replace(old_pair, new_pair, 1)

for old_text, new_text in [
    ('FM-1 A2.1 DIRECT FLASHER', 'FM-1 A2.2 DIRECT FLASHER'),
    ('SCEGLI A2.1 APP', 'SCEGLI A2.2 APP'),
    ('SCRIVI A2.1 PERF', 'SCRIVI A2.2 NORES'),
    ('file APP A2.1 fornito', 'file APP A2.2 fornito'),
    ('APP A2.1 non valido', 'APP A2.2 non valido'),
    ('A2.1 APP verificato', 'A2.2 APP verificato'),
    ('Seleziona prima A2.1 APP', 'Seleziona prima A2.2 APP'),
    ('Scrivere TTG A2.1 PERF?', 'Scrivere TTG A2.2 NORES?'),
    ('fm1-preflash-A2.1-', 'fm1-preflash-A2.2-'),
    ('fm1-A2.1-', 'fm1-A2.2-'),
    ('TTG A2.1 PERF VERIFIED', 'TTG A2.2 NORES CONTROL VERIFIED'),
    ('A2.1 PERF SCRITTO E VERIFICATO', 'A2.2 NORES SCRITTO E VERIFICATO'),
    ('FM-1 A2.1 Flasher', 'FM-1 A2.2 Flasher'),
]:
    s = s.replace(old_text, new_text)

# A2.2 must only accept the exact extracted app slice: 0x4000..0x92fff.
if 'APP_SIZE=0x8F000' not in s or 'APP_LO=0x4000' not in s or 'APP_HI=0x93000' not in s:
    raise SystemExit('protected flash range anchors missing')
if 'beb82556d6528d71f733b1d8ca2d7e282932488777f1a8e73fddccab276e5cfe' not in s:
    raise SystemExit('new A2.2 APP hash not installed')
if 'af469c9bf4012216d346ce889ff93b1592362bf7b7180956cded3669f291c739' not in s:
    raise SystemExit('new A2.2 PRE hash not installed')
if 'bffc26679e36a4384ed0a301cd4c9e8de601074ee80ffd8e0a2400ca6477f9f0' not in s:
    raise SystemExit('new A2.2 FINAL hash not installed')

p.write_text(s)
print('Direct flasher pinned to TTG FM-1 A2.2 NORES CONTROL')
