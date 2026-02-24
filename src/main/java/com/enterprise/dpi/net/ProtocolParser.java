package com.enterprise.dpi.net;

import com.enterprise.dpi.model.PacketCursor;
import java.nio.ByteBuffer;

/**
 * Parses raw bytes into Protocol fields on the PacketCursor.
 * Uses ByteBuffer for zero-allocation parsing.
 */
public class ProtocolParser {

    // Ethernet header size
    private static final int ETHERNET_HEADER_LEN = 14;

    public static void parse(PacketCursor cursor) {
        ByteBuffer buf = cursor.buffer;

        if (buf.remaining() < ETHERNET_HEADER_LEN) {
            return; // Truncated packet
        }

        // Ethernet parsing (skip MAC addresses)
        buf.position(buf.position() + 12); // Dest MAC (6) + Src MAC (6)
        short etherType = buf.getShort();

        if (etherType == (short) 0x0800) { // IPv4
            parseIPv4(cursor, buf);
        }
    }

    private static void parseIPv4(PacketCursor cursor, ByteBuffer buf) {
        if (buf.remaining() < 20)
            return;

        int preIpPos = buf.position();
        byte versionAndIhl = buf.get();
        int ipHeaderLen = (versionAndIhl & 0x0F) * 4;

        // Skip TOS, Total Length, ID, Flags, TTL
        buf.position(buf.position() + 8);

        cursor.protocol = buf.get(); // TCP (6), UDP (17)

        // Skip header checksum
        buf.position(buf.position() + 2);

        cursor.srcIp = buf.getInt();
        cursor.dstIp = buf.getInt();

        // Jump to start of Transport protocol
        buf.position(preIpPos + ipHeaderLen);

        if (cursor.protocol == 6) { // TCP
            parseTCP(cursor, buf);
        } else if (cursor.protocol == 17) { // UDP
            parseUDP(cursor, buf);
        }
    }

    private static void parseTCP(PacketCursor cursor, ByteBuffer buf) {
        if (buf.remaining() < 20)
            return;

        int preTcpPos = buf.position();

        cursor.srcPort = buf.getShort();
        cursor.dstPort = buf.getShort();
        cursor.tcpSeqNum = Integer.toUnsignedLong(buf.getInt());

        // Skip ACK sequence number
        buf.getInt();

        byte dataOffsetAndReserved = buf.get();
        int tcpHeaderLen = ((dataOffsetAndReserved >> 4) & 0x0F) * 4;

        cursor.payloadOffset = preTcpPos + tcpHeaderLen;
        cursor.payloadLength = buf.capacity() - cursor.payloadOffset;
    }

    private static void parseUDP(PacketCursor cursor, ByteBuffer buf) {
        if (buf.remaining() < 8)
            return;

        cursor.srcPort = buf.getShort();
        cursor.dstPort = buf.getShort();

        // Skip Length and Checksum
        buf.getInt();

        cursor.payloadOffset = buf.position();
        cursor.payloadLength = buf.capacity() - cursor.payloadOffset;
    }
}
