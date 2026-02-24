package com.enterprise.dpi.net;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Single-threaded writer that drains the output queue and writes a valid PCAP
 * file.
 */
public class PcapWriter implements Runnable {

    private final BlockingQueue<ByteBuffer> outputQueue;
    private final String outputPath;
    private volatile boolean running = true;

    // PCAP Global Header (24 bytes)
    private static final byte[] GLOBAL_HEADER = new byte[] {
            (byte) 0xd4, (byte) 0xc3, (byte) 0xb2, (byte) 0xa1, // Magic Number (Little Endian)
            0x02, 0x00, // Major Version (2)
            0x04, 0x00, // Minor Version (4)
            0x00, 0x00, 0x00, 0x00, // Timezone
            0x00, 0x00, 0x00, 0x00, // Sigfigs
            (byte) 0xff, (byte) 0xff, 0x00, 0x00, // Snaplen (65535)
            0x01, 0x00, 0x00, 0x00 // Network (1 = Ethernet)
    };

    public PcapWriter(BlockingQueue<ByteBuffer> outputQueue, String outputPath) {
        this.outputQueue = outputQueue;
        this.outputPath = outputPath;
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        try (FileOutputStream fos = new FileOutputStream(outputPath);
                FileChannel channel = fos.getChannel()) {

            // Write Global Header once
            channel.write(ByteBuffer.wrap(GLOBAL_HEADER));

            // Reusable header buffer (16 bytes)
            ByteBuffer headerBuf = ByteBuffer.allocate(16);
            headerBuf.order(ByteOrder.LITTLE_ENDIAN);

            while (running || !outputQueue.isEmpty()) {
                ByteBuffer packet = outputQueue.poll(100, TimeUnit.MILLISECONDS);

                if (packet != null) {
                    // Packet ByteBuffer already contains the exact bytes (including payload)
                    // We need to reconstruct the 16-byte PCAP packet header since we sliced it off
                    // in PcapReader

                    int length = packet.limit();

                    headerBuf.clear();
                    // Fake timestamp (in a real app, we'd preserve the original timestamp from
                    // PcapReader)
                    long timeMs = System.currentTimeMillis();
                    headerBuf.putInt((int) (timeMs / 1000)); // ts_sec
                    headerBuf.putInt((int) (timeMs % 1000) * 1000); // ts_usec
                    headerBuf.putInt(length); // incl_len
                    headerBuf.putInt(length); // orig_len
                    headerBuf.flip();

                    // Write Header
                    channel.write(headerBuf);

                    // Write Data
                    // Reset position to 0 because the limit is properly set from PcapReader slice
                    packet.position(0);
                    channel.write(packet);
                }
            }

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
