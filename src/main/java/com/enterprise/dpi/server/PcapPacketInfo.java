package com.enterprise.dpi.server;

/**
 * Lightweight POJO representing a single packet's parsed metadata.
 * Jackson serializes this to JSON for the /api/packets endpoint.
 */
public class PcapPacketInfo {
    public int id;
    public String srcIp;
    public String dstIp;
    public int srcPort;
    public int dstPort;
    public String protocol;   // "TCP", "UDP", "OTHER"
    public String sni;        // null if not a TLS Client Hello
    public int length;
    public boolean blocked;

    // Default constructor for Jackson
    public PcapPacketInfo() {}

    public PcapPacketInfo(int id, String srcIp, String dstIp,
                          int srcPort, int dstPort, String protocol,
                          String sni, int length, boolean blocked) {
        this.id       = id;
        this.srcIp    = srcIp;
        this.dstIp    = dstIp;
        this.srcPort  = srcPort;
        this.dstPort  = dstPort;
        this.protocol = protocol;
        this.sni      = sni;
        this.length   = length;
        this.blocked  = blocked;
    }

    /** Converts a raw int IP (network byte order) to dotted-decimal string. */
    public static String intToIp(int ip) {
        return ((ip >> 24) & 0xFF) + "." +
               ((ip >> 16) & 0xFF) + "." +
               ((ip >>  8) & 0xFF) + "." +
               ( ip        & 0xFF);
    }

    /** Converts an unsigned short port to int. */
    public static int toUnsignedPort(short port) {
        return Short.toUnsignedInt(port);
    }
}
