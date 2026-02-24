package com.enterprise.dpi.net;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * Memory maps a PCAP file and yields zero-copy byte buffers.
 * Greatly improves IO throughput by skipping Java heap allocations.
 */
public class PcapReader implements AutoCloseable {
    private final RandomAccessFile raf;
    private final FileChannel channel;
    private final ByteBuffer mappedFileData;

    public PcapReader(String pcapFilePath) throws Exception {
        File file = new File(pcapFilePath);
        this.raf = new RandomAccessFile(file, "r");
        this.channel = raf.getChannel();

        // Map the entire file into memory (zero-copy read)
        mappedFileData = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        mappedFileData.order(ByteOrder.LITTLE_ENDIAN); // PCAP usually writes LE header

        // Validate Magic Number
        int magic = mappedFileData.getInt();
        if (magic != 0xa1b2c3d4 && magic != 0xd4c3b2a1) {
            throw new IllegalArgumentException("Not a valid PCAP file");
        }

        // Skip the rest of the 24 byte global header
        mappedFileData.position(24);
    }

    /**
     * Reads the next packet header and returns a buffer pointing to the payload.
     * Returns null if EOF.
     */
    public ByteBuffer readNextPacket() {
        if (mappedFileData.remaining() < 16) {
            return null; // EOF
        }

        // Pcap Packet Header (16 bytes)
        int tsSec = mappedFileData.getInt();
        int tsUsec = mappedFileData.getInt();
        int inclLen = mappedFileData.getInt();
        int origLen = mappedFileData.getInt();

        if (mappedFileData.remaining() < inclLen) {
            return null; // Malformed PCAP
        }

        // Slice a byte buffer for the exact packet bounds zero-copy
        ByteBuffer pktBuffer = mappedFileData.slice();
        pktBuffer.limit(inclLen);
        pktBuffer.order(ByteOrder.BIG_ENDIAN); // Network protocol headers are Big Endian

        // Advance main buffer
        mappedFileData.position(mappedFileData.position() + inclLen);

        return pktBuffer;
    }

    @Override
    public void close() throws Exception {
        channel.close();
        raf.close();
    }
}
