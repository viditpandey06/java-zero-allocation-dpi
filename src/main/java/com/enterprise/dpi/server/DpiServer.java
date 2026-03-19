package com.enterprise.dpi.server;

import com.enterprise.dpi.core.DpiEngine;
import com.enterprise.dpi.core.FastPathWorker;
import com.enterprise.dpi.core.PacketDispatcher;
import com.enterprise.dpi.net.PcapReader;
import com.enterprise.dpi.net.PcapWriter;
import com.enterprise.dpi.rules.RuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class DpiServer {

    private static List<PcapPacketInfo> cachedPackets;
    private static final ObjectMapper mapper = new ObjectMapper();

    public static class RunRequest {
        public List<String> blockedDomains;
        public RunRequest() {}
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        System.out.println("[DpiServer] Extracting bundled demo.pcap ...");
        Path pcapPath = PcapPreParser.extractPcap();
        System.out.println("[DpiServer] Pre-parsing packets for table view ...");
        cachedPackets = PcapPreParser.parse(pcapPath);
        System.out.println("[DpiServer] Loaded " + cachedPackets.size() + " packets into cache.");

        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(rule -> rule.anyHost());
            });
        });

        app.get("/health", ctx -> ctx.result("OK").status(200));

        app.get("/api/packets", ctx -> {
            ctx.contentType("application/json");
            ctx.result(mapper.writeValueAsString(cachedPackets));
        });

        app.post("/api/run", ctx -> {
            try {
                RunRequest req = ctx.bodyAsClass(RunRequest.class);
                RuleManager rules = new RuleManager(req.blockedDomains);
                Map<String, Object> result = runEngine(pcapPath, rules);
                ctx.contentType("application/json");
                ctx.result(mapper.writeValueAsString(result));
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("{\"error\": \"" + e.getMessage() + "\"}");
            }
        });

        app.start(port);
        System.out.println("[DpiServer] Listening on port " + port);
    }

    private static Map<String, Object> runEngine(Path pcapPath, RuleManager rules) throws Exception {
        int numWorkers = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

        List<BlockingQueue<ByteBuffer>> workerQueues = new ArrayList<>();
        List<FastPathWorker> workers = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);

        Path outFile = java.nio.file.Files.createTempFile("dpi-out-", ".pcap");
        outFile.toFile().deleteOnExit();

        BlockingQueue<ByteBuffer> outputQueue = new LinkedBlockingQueue<>();
        PcapWriter writer = new PcapWriter(outputQueue, outFile.toString());
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

        try (PcapReader reader = new PcapReader(pcapPath.toString())) {
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

        long totalDropped  = workers.stream().mapToLong(w -> w.packetsDropped.get()).sum();
        long totalForwarded = workers.stream().mapToLong(w -> w.packetsProcessed.get()).sum();

        Map<String, Integer> blockedDomains = new LinkedHashMap<>();
        for (PcapPacketInfo pkt : cachedPackets) {
            if (pkt.sni != null && rules.isBlocked(pkt.sni)) {
                blockedDomains.merge(pkt.sni, 1, Integer::sum);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalPackets",  totalPackets);
        result.put("durationMs",    durationMs);
        result.put("pps",           (long) pps);
        result.put("forwarded",     totalForwarded);
        result.put("dropped",       totalDropped);
        result.put("workers",       numWorkers);
        result.put("blockedDomains", blockedDomains);

        return result;
    }
}
