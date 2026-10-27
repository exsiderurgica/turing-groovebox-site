# EXS MT4 — Flasher universale Chrome / Nothing

**URL pubblico fisso:** https://exsiderurgica.github.io/turing-groovebox-site/mt4-flasher/

Questo è il flasher WebMIDI USB-C **unico** per il firmware EXS MT4 su M-VAVE FM1, non uno per ogni versione P5.1/P5.6/P5.8.
La pagina permette di selezionare dal telefono un qualunque `.fwsc` EXS MT4 attendibile, senza `release.json` / hash SHA-256 / nome build predefiniti da aggiornare ogni volta.

## Procedura MT4 da mantenere SEMPRE

1. Compilare la nuova build firmware `.fwsc` nella CI del repository **exsiderurgica/EXS-FM1**.
2. Verificare i test host, build JieLi AC791N, OTA packaging e dimensioni. Tenere una GOLD nota per ripristino.
3. Fornire nella risposta il **file `.fwsc` reale, accessibile e verificato**, NON un ZIP, testo base64 o percorso inventato.
4. Fornire sempre il link **https://exsiderurgica.github.io/turing-groovebox-site/mt4-flasher/**.
5. Utente: Chrome Android sul Nothing → USB-C dati → aprire flasher → selezionare `.fwsc` locale → verificare porta/identità MIDI/SysEx → confermare precauzioni → installare.
6. **Non modificare né ripubblicare il flasher per la nuova versione**, a meno di un cambiamento effettivo del protocollo OTA/WebMIDI o di un bug del flasher.
7. La build compilata e i test host non equivalgono a validazione fisica. Il successo dell'OTA va verificato sull'FM1; non presentare una build sperimentale come GOLD.
8. Se il dispositivo non risponde al handshake OTA o si trova in UBOOT, questa pagina non è una procedura di ripristino: usare solo il metodo di recupero hardware già collaudato.

## Verifiche locali del flasher

- Estensione `.fwsc`, lunghezza 300KB–4MB, identificatore `FM-1_9xx`, loader Felucca presente, immagine logica coerente.
- Impronta SHA-256 **calcolata nel browser sul file selezionato**, ricontrollata prima dell'OTA. Non è una firma crittografica, quindi **non certifica l'origine** del file.
- Porta MIDI con identità rispondente, modello compatibile e loader OTA noto; niente fallback cieco a Felucca senza handshake.
- L'upload del file al server non avviene: selezione e trasporto SysEx operano localmente.
- Conferma esplicita prima di scrivere e nessuna scrittura se il device cambia identità.
- Il trasporto Felucca/SLOOP richiede che il firmware attualmente avviato supporti OTA: la sola enumerazione USB MIDI non basta.
- `fm1-test-a03/fm1ota.js` è il livello di trasporto riutilizzato, GPL-3.0-only, crediti Felucca e SLOOP conservati.

## Test automatici e limitazioni

- `node --experimental-default-type=module --test mt4-flasher/fwsc-check.test.mjs`
- GitHub Actions: `.github/workflows/mt4-universal-flasher-check.yml`
- Convalida software senza accedere all'hardware reale: successi host **non sono prova** del completamento effettivo dell'OTA da Android Chrome.
- Per evitare aggiornamenti inutili, **nessun firmware `.fwsc` viene pubblicato automaticamente nel sito**. Il flasher accetta il file locale distribuito nella conversazione o tramite canali verificati.

I vecchi percorsi `mt4-flasher-v030-p5*` sono storici e potrebbero rifiutare nuove build con SHA diverso; non usarli per le prossime P5.x. Questo percorso `mt4-flasher/` deve restare quello canonico.
