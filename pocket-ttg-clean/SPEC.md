# Pocket TTG FX — clean rebuild

This branch is a clean rebuild of TTG FX for Sonicake Pocket Master.

## Non-negotiable base rule

Every firmware image MUST be generated directly from **Official Pocket Master V1.3.3**.

Expected SHA-256:

`d9112f12a37e3731e540b325d2b9788f005de7f640c129ac30465ea1dc57e7a3`

No prior TTG test image (`v1.7`, `v1.8`, UI probes, palette probes, filter probes, etc.) may ever be used as an input/base image.

## Product identity / boot

Target boot flow:

1. hardware/runtime init retained from official firmware
2. black boot screen
3. `TTG FX` logo
4. enter directly into EFFECTS

No Sonicake preset workflow should be visible in normal TTG FX use.

## Main signal chain

`ANALOG IN + USB IN -> DRIVE 1 -> FILTER 1 -> FILTER 2 -> DRIVE 2 -> DELAY -> CLOUD -> ANALOG OUT + USB OUT`

Each block has independent bypass. A global MASTER BYPASS is also required.

## Main pages

Navigation order:

`D1 -> F1 -> F2 -> D2 -> DLY -> CLOUD -> D1 ...`

Home/overview order:

`IN | D1 | F1 | F2 | D2 | DLY | CLD | OUT | SYS`

### D1
- DRIVE
- COLOR
- LEVEL
- BYPASS

### F1
- LEVEL IN
- CUTOFF
- RESONANCE
- DRIVE
- MODE: HP / LP
- BYPASS

### F2
- LEVEL IN
- CUTOFF
- RESONANCE
- DRIVE
- MODE: HP / LP
- BYPASS

### D2
- DRIVE
- TONE
- LEVEL
- BYPASS

### DLY
- DRY / WET
- TIME
- FEEDBACK
- COLOR
- BYPASS

### CLOUD
- POSITION
- SIZE
- DENSITY
- TEXTURE
- PITCH
- SPREAD / STEREO
- FEEDBACK
- REVERB
- MIX / WET
- FREEZE
- BYPASS

## Hardware navigation target

- LEFT / RIGHT: previous / next block
- VALUE/VOL rotate: edit selected parameter
- VALUE/VOL press: enter / toggle selected item
- independent block bypass
- MASTER BYPASS available globally
- system menu separate from the effect pages

## I/O / system target

Settings must eventually expose:
- analog input level
- USB input level
- analog output level
- USB output level
- USB -> FX bypass
- MASTER FX bypass
- USB loop protection

## Development rule

Stages must be additive and auditable:

- Stage A: clean stock-only UI naming skeleton
- Stage B: clean TTG FX boot/logo and visual shell
- Stage C: deterministic hardware navigation / pages
- Stage D: rebuild DSP/routing block-by-block from official stock
- Stage E: I/O/settings + master bypass + USB routing

Every stage must publish a manifest containing base SHA, output SHA, changed regions and integrity checks.
