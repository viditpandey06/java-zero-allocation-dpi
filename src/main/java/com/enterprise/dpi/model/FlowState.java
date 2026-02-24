package com.enterprise.dpi.model;

import java.nio.ByteBuffer;
import java.util.TreeMap;

/**
 * Tracks the state of a single TCP/UDP connection.
 * Includes TCP reassembly buffers.
 */
public class FlowState {
    private long expectedNextSeq = -1;
    private final TreeMap<Long, byte[]> outOfOrderBuffer = new TreeMap<>();
    
    private ByteBuffer reassembledData = ByteBuffer.allocate(8192); // 8KB buffer for TLS Client Hello
    private boolean inspected = false;
    private boolean blocked = false;
    
    public long getExpectedNextSeq() {
        return expectedNextSeq;
    }

    public void setExpectedNextSeq(long seq) {
        this.expectedNextSeq = seq;
    }

    public void advanceExpectedSeq(int length) {
        this.expectedNextSeq += length;
    }

    public void appendData(ByteBuffer buffer, int offset, int length) {
        if (reassembledData.remaining() < length) {
            // If ClientHello is too large, or we already inspected it, stop buffering
            return;
        }
        
        int oldPos = buffer.position();
        int oldLimit = buffer.limit();
        
        buffer.position(offset);
        buffer.limit(offset + length);
        
        reassembledData.put(buffer);
        
        buffer.limit(oldLimit);
        buffer.position(oldPos);
    }
    
    public void appendData(byte[] data) {
        if (reassembledData.remaining() < data.length) return;
        reassembledData.put(data);
    }

    public void addOutOfOrderSegment(long seqNum, byte[] data) {
        // Cap the memory usage of the out of order buffer to prevent exhaustion
        if (outOfOrderBuffer.size() < 100) {
            outOfOrderBuffer.put(seqNum, data);
        }
    }

    public TreeMap<Long, byte[]> getOutOfOrderBuffer() {
        return outOfOrderBuffer;
    }

    public boolean isInspected() {
        return inspected;
    }

    public void setInspected(boolean inspected) {
        this.inspected = inspected;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public boolean hasEnoughDataForTls() {
        // TLS Client Hello requires at least a few bytes to parse the header and SNI
        return reassembledData.position() > 43; 
    }

    public ByteBuffer getReassembledBuffer() {
        ByteBuffer readOnly = reassembledData.asReadOnlyBuffer();
        readOnly.flip();
        return readOnly;
    }
}
