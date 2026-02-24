package com.enterprise.dpi.core;

import com.enterprise.dpi.inspection.SniExtractor;
import com.enterprise.dpi.model.FlowState;
import com.enterprise.dpi.model.PacketCursor;
import com.enterprise.dpi.net.ProtocolParser;
import com.enterprise.dpi.net.TcpReassembler;
import com.enterprise.dpi.rules.RuleManager;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The FastPathWorker processes assigned packets.
 * Since the PacketDispatcher uses consistent hashing by 5-tuple,
 * all packets for a flow arrive here sequentially. ThreadLocal data structures
 * can be used safely without locks.
 */
public class FastPathWorker implements Runnable {

    private final BlockingQueue<ByteBuffer> inputQueue;
    private final BlockingQueue<ByteBuffer> outputQueue;
    private final RuleManager rules;

    // Primitive mapping for lower memory overhead in production, using HashMap for
    // now
    private final Map<Long, FlowState> flowTable = new HashMap<>();

    public final AtomicLong packetsProcessed = new AtomicLong(0);
    public final AtomicLong packetsDropped = new AtomicLong(0);

    public FastPathWorker(BlockingQueue<ByteBuffer> inputQueue,
            BlockingQueue<ByteBuffer> outputQueue,
            RuleManager rules) {
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
        this.rules = rules;
    }

    @Override
    public void run() {
        PacketCursor cursor = new PacketCursor();
        TcpReassembler reassembler = new TcpReassembler();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                // In a true high-perf scenario, use Disruptor SequenceBarrier here
                ByteBuffer packetBuffer = inputQueue.take();

                // Copy limits to prevent corrupting the buffer when parsing
                ByteBuffer processingBuf = packetBuffer.duplicate();

                cursor.reset(processingBuf);
                ProtocolParser.parse(cursor);

                if (cursor.protocol == 0) { // Unrecognized / Not IP
                    outputQueue.put(packetBuffer);
                    packetsProcessed.incrementAndGet();
                    continue;
                }

                long flowHash = cursor.getFiveTupleHash();
                FlowState flow = flowTable.computeIfAbsent(flowHash, k -> new FlowState());

                // Reassemble streams and inspect if it's TCP
                if (cursor.protocol == 6) {
                    reassembler.processSegment(cursor, flow);

                    if (!flow.isInspected() && flow.hasEnoughDataForTls()) {
                        String sni = SniExtractor.extract(flow.getReassembledBuffer());
                        if (sni != null) {
                            flow.setBlocked(rules.isBlocked(sni));
                            flow.setInspected(true);
                        } else if (flow.getReassembledBuffer().limit() > 2000) {
                            // Give up searching after enough bytes are collected and no SNI found
                            flow.setInspected(true);
                        }
                    }
                }

                // Drop or Forward based on rules
                if (flow.isBlocked()) {
                    packetsDropped.incrementAndGet();
                    // In a real memory pooled implementation, we release the buffer back to pool
                    // here
                } else {
                    packetsProcessed.incrementAndGet();
                    outputQueue.put(packetBuffer);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
