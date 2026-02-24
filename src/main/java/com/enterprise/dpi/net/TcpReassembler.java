package com.enterprise.dpi.net;

import com.enterprise.dpi.model.FlowState;
import com.enterprise.dpi.model.PacketCursor;

import java.nio.ByteBuffer;
import java.util.TreeMap;

/**
 * Reassembles TCP streams handling out-of-order packets.
 * Maintains state inside individual FlowStates to avoid global locking.
 */
public class TcpReassembler {

    public void processSegment(PacketCursor packet, FlowState flow) {
        long seqNum = packet.tcpSeqNum;

        // First packet we see for this flow
        if (flow.getExpectedNextSeq() == -1) {
            // Check for SYN flag (not parsing here directly, but normally would handle
            // SYN/ACK)
            // For simplicity, we just initialize the sequence on the first data packet
            flow.setExpectedNextSeq(seqNum);
        }

        // Exact expected next packet
        if (seqNum == flow.getExpectedNextSeq()) {
            if (packet.payloadLength > 0) {
                flow.appendData(packet.buffer, packet.payloadOffset, packet.payloadLength);
            }
            // Add length to expected seq, treating flags (SYN/FIN) as length 1 is omitted
            // here for simplicity
            flow.advanceExpectedSeq(packet.payloadLength);

            checkOutOfOrderBuffer(flow);
        } else if (seqNum > flow.getExpectedNextSeq()) {
            // Out of order: arrived early!
            if (packet.payloadLength > 0) {
                byte[] segmentData = new byte[packet.payloadLength];
                int oldPos = packet.buffer.position();

                packet.buffer.position(packet.payloadOffset);
                packet.buffer.get(segmentData);
                packet.buffer.position(oldPos);

                flow.addOutOfOrderSegment(seqNum, segmentData);
            }
        }
        // If seqNum < expected, it's a retransmission. Ignore it.
    }

    private void checkOutOfOrderBuffer(FlowState flow) {
        TreeMap<Long, byte[]> outOfOrder = flow.getOutOfOrderBuffer();

        // Fast path check
        if (outOfOrder.isEmpty()) {
            return;
        }

        while (!outOfOrder.isEmpty()) {
            Long nextSeqInMap = outOfOrder.firstKey();
            if (nextSeqInMap == flow.getExpectedNextSeq()) {
                byte[] data = outOfOrder.remove(nextSeqInMap);
                flow.appendData(data);
                flow.advanceExpectedSeq(data.length);
            } else {
                // The gap is not yet filled
                break;
            }
        }
    }
}
