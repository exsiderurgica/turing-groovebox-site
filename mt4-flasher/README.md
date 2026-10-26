# EXS MT4 browser flasher (Nothing / Chrome)

Public page: https://exsiderurgica.github.io/turing-groovebox-site/mt4-flasher/

**CURRENT MODE: USB-MIDI read-only identity diagnostic; flash is disabled.** There is no MT4 P5 .fwsc released or verified yet.

Uses HTTPS WebMIDI with SysEx, the existing `../fm1-test-a03/fm1ota.js` transport and `fm1pkg.js` parser (derived from Felucca/SLOOP, GPL-3.0-only). No USB mass-storage/UBOOT writing. Client never uploads the chosen firmware file to a server.

## Critical first-transition limitation

The previously built P4 workflow explicitly set `FELUCCA_OTA=0` and used JieLi/WL82 UBOOT over ThinkPad. Therefore a P3/P4 device can show a usable USB MIDI port but **cannot complete the two-stage WebMIDI OTA protocol**. The page's `Verifica connessione USB-MIDI` button only requests identity. It refuses flashing without a positive OTA handshake. Do not enable a blind unverified Felucca fallback. A one-time bootstrapping flash may be necessary via the known PC/Mac UBOOT process.

## P5 firmware checklist (not yet implemented)

- Build from approved P5 sources **with `FELUCCA_OTA=1` and `FELUCCA_FLASH=1`**, preserving UAC, TRS MIDI and dual-core timing. Check CPU budget, flash size, .pool, .lowram and OTA staging sectors.
- Verify the matching `FM-1_9xx` product identity and that the .fwsc contains both `flash.bin` and a supported `ota.bin` with the expected 16-KiB stock flash prefix.
- Test the OTA handshake and staged dry-run *on real FM-1* before allowing writes. A USB MIDI port alone does **not** prove OTA is enabled.
- Test restart into OTA loader (USB PID/name change) and Android Chrome MIDI re-enumeration on the Nothing with a recovery path available. Do not first-test a new bootloader on an irreplaceable device.
- Create an approved public `firmware/EXS-MT4-P5.fwsc` only if distribution of all bundled components complies with their licences. For a local-file-only release, leave `firmware_url: null`.
- Set `release.json` with `flash_enabled: true`, `ota_enabled: true`, exact `release` (e.g. `MT4 v0.30 P5`), `product`, byte `size`, lowercase `sha256` of the **whole .fwsc**, and optional same-origin relative `firmware_url`.
- Do not set `flash_enabled: true` before an authorized, hardware-reviewed build exists. Never assume the OTA transition works from currently installed P3/P4; use the known UBOOT transition first if required.
- Do not replace the stable v0.29 GOLD or approved P3 backup.

## Diagnostic test on Android (safe)

Open the public URL in updated Chrome, attach FM-1 by direct USB-C data cable, tap **Verifica connessione USB-MIDI**, grant permission for SysEx and inspect the detected ports and identity. With P3/P4 compiled `OTA=0`, an identity probe may fail; this is expected and **does not imply that USB audio or MIDI clock is broken**.

## Flash safety

The UI blocks writing until the whole .fwsc matches the approved byte count, product string and SHA-256; the browser obtains a device identity and checks known OTA loader IDs. A loader not supported by the Felucca/SLOOP OTA path is not written. If UBOOT is shown instead of MIDI, stop and use the desktop recovery process. Test restore/recovery before distribution.

Sources and credits: [SLOOP](https://github.com/isod89/sloop-fm1), [Felucca](https://github.com/hugelton/Felucca); reused browser protocol resides in `fm1-test-a03/` and retains upstream GPL-3.0-only attribution.
