package com.hyped.app.identity.infrastructure.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

final class CryptoEncoding {
    private CryptoEncoding() {
    }

    // Each UTF-8 component is preceded by its unsigned-compatible, big-endian 32-bit byte length.
    static byte[] encode(String... components) {
        byte[][] encoded = new byte[components.length][];
        int length = 0;
        for (int i = 0; i < components.length; i++) {
            encoded[i] = components[i].getBytes(StandardCharsets.UTF_8);
            length = Math.addExact(length, Math.addExact(Integer.BYTES, encoded[i].length));
        }
        ByteBuffer result = ByteBuffer.allocate(length);
        for (byte[] component : encoded) {
            result.putInt(component.length).put(component);
            java.util.Arrays.fill(component, (byte) 0);
        }
        return result.array();
    }
}
