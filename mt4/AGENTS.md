# EXS MT4 public site — Codex scoped instructions

This folder (`mt4/`) is a **public distribution website**, not the MT4 firmware source tree. The canonical source project is the private `exsiderurgica/EXS-FM1` repository, with GOLD release tracking in `docs/MT4_CURRENT_GOLD.md` there.

- `mt4/` contains the current public page, manual, legacy/static firmware downloads and related assets.
- The canonical universal Chrome flasher is **`../mt4-flasher/`**, not one of the version-specific experimental flasher folders.
- Firmware development and testing happen in EXS-FM1. Never create, promote, overwrite, or advertise an experimental `.fwsc` as GOLD on this site without owner approval, native build verification and known SHA-256.
- Do not change the public `mt4/index.html`, JavaScript, firmware binaries, release labels or flasher as part of an unrelated firmware change. Treat publication as a separate, explicitly authorized task.
- Preserve existing download URLs and recovery instructions; do not weaken browser/OTA identity checks. Follow the source repository's `docs/MT4_CHROME_FLASH_WORKFLOW.md`.
- Do not import content from `videoblock-site`: VideoBlock and its historical `noctis/` prototype are separate projects, not MT4 implementation dependencies.
- Changes must be proposed on a feature branch and checked by the owner before merge/publication.
