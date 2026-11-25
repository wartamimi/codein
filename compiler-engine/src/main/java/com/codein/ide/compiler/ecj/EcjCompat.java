package com.codein.ide.compiler.ecj;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Java 9+ {@link InputStream} methods for Android versions that lack them (before API 33). The
 * build rewrites ECJ's calls to {@code InputStream.readAllBytes()} and {@code readNBytes(int)}
 * to these static methods; see {@code patchEcj} in compiler-engine/build.gradle.kts.
 */
public final class EcjCompat {
    private EcjCompat() {
    }

    public static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(in.available(), 8192));
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    public static byte[] readNBytes(InputStream in, int len) throws IOException {
        if (len < 0) {
            throw new IllegalArgumentException("len < 0");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(len, 8192));
        byte[] buffer = new byte[8192];
        int remaining = len;
        while (remaining > 0) {
            int n = in.read(buffer, 0, Math.min(buffer.length, remaining));
            if (n == -1) {
                break;
            }
            out.write(buffer, 0, n);
            remaining -= n;
        }
        return out.toByteArray();
    }
}
