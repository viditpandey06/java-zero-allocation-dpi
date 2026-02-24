package com.enterprise.dpi.model;

import java.nio.ByteBuffer;

/**
 * Flyweight pattern object to avoid object allocation in the hot path.
 * Instead of creating a new Object for every packet, we reuse this cursor
 * and update its primitive fields.
 */
public class PacketCursor {
    public ByteBuffer buffer;
    
    // Primitive fields to prevent allocations
    public int srcIp;
    public int dstIp;
    public short srcPort;
    public short dstPort;
    public byte protocol;
    
    public int payloadOffset;
    public int payloadLength;
    public long tcpSeqNum;
    
    public PacketCursor() {
    }

    public void reset(ByteBuffer buffer) {
        this.buffer = buffer;
        this.srcIp = 0;
        this.dstIp = 0;
        this.srcPort = 0;
        this.dstPort = 0;
        this.protocol = 0;
        this.payloadOffset = 0;
        this.payloadLength = 0;
        this.tcpSeqNum = 0;
    }
    
    /**
     * Compute a hash of the 5-tuple for flow tracking and consistent hashing.
     */
    public long getFiveTupleHash() {
        // High-performance mixing formula
        long hash = 17;
        hash = hash * 31 + srcIp;
        hash = hash * 31 + dstIp;
        hash = hash * 31 + srcPort;
        hash = hash * 31 + dstPort;
        hash = hash * 31 + protocol;
        return hash;
    }
}
