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
            System.err.println("Usage: java -cp bin com.enterprise.dpi.core.DpiEngine <input.pcap> <output.pcap>");
            System.exit(1);
        }

        String pcapFile = args[0];
        String outputFile = args[1];

        // Performance tuning
        int numWorkers = Runtime.getRuntime().availableProcessors() - 1;
        if (numWorkers < 1)
            numWorkers = 1;

        System.out.println("Starting Enterprise DPI Engine in Java");
        System.out.println("Spawning " + numWorkers + " FastPath workers");

        RuleManager rules = new RuleManager();
        List<BlockingQueue<ByteBuffer>> workerQueues = new ArrayList<>();
        List<FastPathWorker> workers = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);

        // Output queue configuration
        // Switched from ArrayBlockingQueue to LinkedBlockingQueue (Unbounded)
        // so the system doesn't deadlock when processing an 800k packet burst file and
        // the single writer falls behind.
        BlockingQueue<ByteBuffer> outputQueue = new LinkedBlockingQueue<>();

        // Start the Writer thread
        PcapWriter writer = new PcapWriter(outputQueue, outputFile);
        Thread writerThread = new Thread(writer, "PcapWriterThread");
        writerThread.start();

        // Initialize workers and their queues
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

        // Start processing PCAP zero-copy style
        try (PcapReader reader = new PcapReader(pcapFile)) {
            ByteBuffer packet;
            while ((packet = reader.readNextPacket()) != null) {
                dispatcher.dispatch(packet);
                totalPackets++;
            }
        }

        System.out.println("Finished reading PCAP. Waiting for workers to drain queues...");

        // Shutdown sequence
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Stop writer and wait
        writer.stop();
        writerThread.join(2000);

        // Calculate benchmark metrics
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        double pps = (double) totalPackets / (durationMs / 1000.0);

        long totalDropped = workers.stream()
                .mapToLong(w -> w.packetsDropped.get())
                .sum();
        long totalProcessed = workers.stream()
                .mapToLong(w -> w.packetsProcessed.get())
                .sum();

        System.out.println("\n----------------- BENCHMARK -----------------");
        System.out.printf("Processed %,d packets in %d ms%n", totalPackets, durationMs);
        System.out.printf("Throughput: %,.0f PPS (Packets Per Second)%n", pps);
        System.out.printf("Packets Forwarded: %,d%n", totalProcessed);
        System.out.printf("Packets Dropped: %,d%n", totalDropped);
        System.out.println("---------------------------------------------");

        System.exit(0);
    }
}
