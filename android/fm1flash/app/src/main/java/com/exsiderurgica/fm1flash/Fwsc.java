package com.exsiderurgica.fm1flash;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

final class Fwsc {
    static final int APP_END = 0x93000;
    static final int WRITE_MIN = 0x4000;
    static final int SECTOR = 0x1000;
    static final String REQUIRED_SHA256 = "2eaea4bd094715d02a5e89e224087d4173f7432a2143f7757fd3209bc9e311f2";
    final byte[] raw;
    final byte[] flash;
    final String sha256;

    private Fwsc(byte[] raw, byte[] flash, String sha256) {
        this.raw = raw;
        this.flash = flash;
        this.sha256 = sha256;
    }

    static Fwsc read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        byte[] raw = out.toByteArray();
        String hash = sha256(raw);
        if (!REQUIRED_SHA256.equals(hash)) throw new IOException("Firmware non riconosciuto. SHA256=" + hash);
        if (raw.length < 20 * 0x30) throw new IOException("FWSC troppo corto");

        ByteArrayOutputStream logicalOut = new ByteArrayOutputStream(raw.length);
        for (int i = 0; i < 20; i++) logicalOut.write(raw, i * 0x30, 0x2F);
        logicalOut.write(raw, 20 * 0x30, raw.length - 20 * 0x30);
        byte[] logical = logicalOut.toByteArray();

        byte[] hdr = Arrays.copyOfRange(logical, 0, 0x40);
        enc(hdr);
        int entries = u16le(hdr, 8);
        if (!Arrays.equals(Arrays.copyOfRange(hdr, 16, 22), "AC791N".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
            throw new IOException("Header FWSC non FM-1");
        if (entries < 1 || entries > 15) throw new IOException("Numero entry FWSC non valido: " + entries);

        for (int i = 0; i < entries; i++) {
            int pos = 0x40 + i * 0x50;
            if (pos + 0x50 > logical.length) throw new IOException("Entry FWSC troncata");
            byte[] e = Arrays.copyOfRange(logical, pos, pos + 0x50);
            enc(e);
            int type = u16le(e, 0);
            int dcrc = u16le(e, 4);
            long offL = u32le(e, 8);
            long sizeL = u32le(e, 12);
            if (type != 0) continue;
            if (offL < 0 || sizeL != APP_END || offL + sizeL > logical.length)
                throw new IOException(String.format(Locale.US, "flash.bin inatteso off=%X size=%X", offL, sizeL));
            byte[] flash = Arrays.copyOfRange(logical, (int)offL, (int)(offL + sizeL));
            if (crc16(flash, 0) != dcrc) throw new IOException("CRC flash.bin non valido");
            return new Fwsc(raw, flash, hash);
        }
        throw new IOException("flash.bin non trovato nel FWSC");
    }

    static int crc16(byte[] data, int crc) {
        for (byte xb : data) {
            crc ^= (xb & 255) << 8;
            for (int i = 0; i < 8; i++) crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
        }
        return crc;
    }

    private static void enc(byte[] b) {
        int key = 0xFFFF;
        for (int i = 0; i < b.length; i++) {
            b[i] ^= (byte)(key & 255);
            key = ((key << 1) ^ ((key & 0x8000) != 0 ? 0x1021 : 0)) & 0xFFFF;
        }
    }

    private static int u16le(byte[] b, int o) { return (b[o] & 255) | ((b[o + 1] & 255) << 8); }
    private static long u32le(byte[] b, int o) {
        return ((long)b[o] & 255) | (((long)b[o+1] & 255) << 8) | (((long)b[o+2] & 255) << 16) | (((long)b[o+3] & 255) << 24);
    }

    static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] h = md.digest(data);
        StringBuilder s = new StringBuilder();
        for (byte x : h) s.append(String.format(Locale.US, "%02x", x & 255));
        return s.toString();
    }
}
