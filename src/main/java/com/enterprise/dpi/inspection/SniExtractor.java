package com.enterprise.dpi.inspection;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Extracts Server Name Indication (SNI) from TLS Client Hello.
 * Operates safely on raw ByteBuffers holding reassembled streams.
 */
public class SniExtractor {

    public static String extract(ByteBuffer buffer) {
        if (buffer.remaining() < 43) {
            return null; // Too small
        }

        // Must be Handshake (0x16)
        if (buffer.get() != 0x16)
            return null;

        // Version (usually 0x0301 or 0x0303)
        buffer.getShort();

        // Read record length (we ignore it for stream reassembled data)
        buffer.getShort();

        // Must be Client Hello (0x01)
        if (buffer.get() != 0x01)
            return null;

        // Handshake length (3 bytes)
        int length1 = Byte.toUnsignedInt(buffer.get());
        int length2 = Byte.toUnsignedInt(buffer.get());
        int length3 = Byte.toUnsignedInt(buffer.get());
        int handshakeLength = (length1 << 16) | (length2 << 8) | length3;

        // Skip client version (2 bytes) + Random (32 bytes)
        buffer.position(buffer.position() + 34);

        // Session ID
        int sessionIdLen = Byte.toUnsignedInt(buffer.get());
        buffer.position(buffer.position() + sessionIdLen);

        // Cipher suites
        int cipherSuitesLen = Short.toUnsignedInt(buffer.getShort());
        buffer.position(buffer.position() + cipherSuitesLen);

        // Compression methods
        int compMethodsLen = Byte.toUnsignedInt(buffer.get());
        buffer.position(buffer.position() + compMethodsLen);

        // Extensions
        if (buffer.remaining() < 2)
            return null;
        int extLen = Short.toUnsignedInt(buffer.getShort());

        int limit = buffer.position() + extLen;
        if (limit > buffer.capacity()) {
            limit = buffer.capacity();
        }

        while (buffer.position() + 4 <= limit && buffer.remaining() >= 4) {
            int extType = Short.toUnsignedInt(buffer.getShort());
            int extDataLen = Short.toUnsignedInt(buffer.getShort());

            if (extType == 0x0000) { // SNI
                // Skip server name list length (2 bytes)
                buffer.getShort();

                // Server name type (0 = HostName)
                int nameType = Byte.toUnsignedInt(buffer.get());
                if (nameType == 0) {
                    int sniLen = Short.toUnsignedInt(buffer.getShort());
                    if (buffer.remaining() >= sniLen) {
                        byte[] sniBytes = new byte[sniLen];
                        buffer.get(sniBytes);
                        return new String(sniBytes, StandardCharsets.UTF_8);
                    }
                }
                return null;
            } else {
                buffer.position(buffer.position() + extDataLen);
            }
        }

        return null; // SNI not found
    }
}
