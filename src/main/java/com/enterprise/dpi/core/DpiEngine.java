package com.enterprise.dpi.core;

import com.enterprise.dpi.net.PcapReader;
import com.enterprise.dpi.net.PcapWriter;
import com.enterprise.dpi.rules.RuleManager;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main bootstrapper for the engine.
 * Demonstrates high-performance thread orchestration and benchmarking.
 */
public class DpiEngine {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: DpiEngine <input.pcap> <output.pcap>");
            return;
        }

        String inputPcap = args[0];
        String outputPcap = args[1];

        // Empty rules by default for the CLI demo
        RuleManager rules = new RuleManager(java.util.Collections.emptySet());
        
        int numWorkers = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        System.out.println("Starting DPI Engine with " + numWorkers + " workers...");

        List<BlockingQueue<ByteBuffer>> workerQueues = new ArrayList<>();
        List<FastPathWorker> workers = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);

        BlockingQueue<ByteBuffer> outputQueue = new LinkedBlockingQueue<>();

        PcapWriter writer = new PcapWriter(outputQueue, outputPcap);
        Thread writerThread = new Thread(writer, "PcapWriterThread");
        writerThread.start();

        for (int i = 0; i < numWorkers; i++) {
            LinkedBlockingQueue<ByteBuffer> q = new LinkedBlockingQueue<>();
            workerQueues.add(q);
            FastPathWorker worker = new FastPathWorker(q, outputQueue, rules);
            workers.add(worker);
            executor.submit(worker);
        }

        PacketDispatcher dispatcher = new PacketDispatcher(workerQueues);

        long startTime = System.nanoTime();
        long totalPackets = 0;

        try (PcapReader reader = new PcapReader(inputPcap)) {
            ByteBuffer packet;
            while ((packet = reader.readNextPacket()) != null) {
                dispatcher.dispatch(packet);
                totalPackets++;
            }
        }

        for (BlockingQueue<ByteBuffer> queue : workerQueues) {
            queue.put(FastPathWorker.POISON_PILL);
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        writer.stop();
        writerThread.join(2000);

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        if (durationMs == 0) durationMs = 1;
        
        double pps = (double) totalPackets / (durationMs / 1000.0);

        long totalDropped = workers.stream().mapToLong(w -> w.packetsDropped.get()).sum();
        long totalProcessed = workers.stream().mapToLong(w -> w.packetsProcessed.get()).sum();

        System.out.println("\n----------------- BENCHMARK -----------------");
        System.out.printf("Processed %,d packets in %d ms%n", totalPackets, durationMs);
        System.out.printf("Throughput: %,.0f PPS%n", pps);
        System.out.printf("Packets Forwarded: %,d%n", totalProcessed);
        System.out.printf("Packets Dropped: %,d%n", totalDropped);
        System.out.println("---------------------------------------------");
    }
}
