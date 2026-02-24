package com.enterprise.dpi.core;

import com.enterprise.dpi.model.PacketCursor;
import com.enterprise.dpi.net.ProtocolParser;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Receives raw packets from Reader and consistently hashes them to FastPath
 * workers.
 * Ensures flow affinity so TCP reassemblers don't require thread locking.
 */
public class PacketDispatcher {

    private final List<BlockingQueue<ByteBuffer>> workerQueues;
    private final int numWorkers;

    public PacketDispatcher(List<BlockingQueue<ByteBuffer>> workerQueues) {
        this.workerQueues = workerQueues;
        this.numWorkers = workerQueues.size();
    }

    public void dispatch(ByteBuffer packet) {
        // We do a quick parse to get the 5-tuple
        PacketCursor cursor = new PacketCursor();

        // Duplicate so we don't mess up the position for FastPath
        cursor.reset(packet.duplicate());
        ProtocolParser.parse(cursor);

        // Route non-IP or non-TCP/UDP traffic to worker 0
        if (cursor.protocol == 0) {
            try {
                workerQueues.get(0).put(packet);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }

        long hash = cursor.getFiveTupleHash();
        int workerIndex = Math.abs((int) (hash % numWorkers));

        try {
            workerQueues.get(workerIndex).put(packet);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
