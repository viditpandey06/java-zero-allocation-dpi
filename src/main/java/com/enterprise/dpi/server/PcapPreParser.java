package com.enterprise.dpi.server;

import com.enterprise.dpi.inspection.SniExtractor;
import com.enterprise.dpi.model.PacketCursor;
import com.enterprise.dpi.net.ProtocolParser;
import com.enterprise.dpi.net.PcapReader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the bundled demo.pcap on server startup and builds a list of
 * PcapPacketInfo objects. 
 */
public class PcapPreParser {

    private static final String RESOURCE_PATH = "/pcap/demo.pcap";
    private static Path tempPcapPath;

    public static Path extractPcap() throws Exception {
        if (tempPcapPath != null && Files.exists(tempPcapPath)) {
            return tempPcapPath;
        }
        try (InputStream in = PcapPreParser.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Bundled demo.pcap not found in JAR at: " + RESOURCE_PATH);
            }
            tempPcapPath = Files.createTempFile("dpi-demo-", ".pcap");
            tempPcapPath.toFile().deleteOnExit();
            Files.copy(in, tempPcapPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempPcapPath;
    }

    public static List<PcapPacketInfo> parse(Path pcapPath) throws Exception {
        List<PcapPacketInfo> packets = new ArrayList<>();
        PacketCursor cursor = new PacketCursor();
        int id = 1;

        try (PcapReader reader = new PcapReader(pcapPath.toString())) {
            ByteBuffer packet;
            while ((packet = reader.readNextPacket()) != null && packets.size() < 2400) {
                ByteBuffer dup = packet.duplicate();
                cursor.reset(dup);
                ProtocolParser.parse(cursor);

                String protocolStr;
                if (cursor.protocol == 6)       protocolStr = "TCP";
                else if (cursor.protocol == 17)  protocolStr = "UDP";
                else                             protocolStr = "OTHER";

                String sni = null;
                boolean blocked = false; // Resolved in UI dynamically
                if (cursor.protocol == 6 && cursor.payloadLength > 0) {
                    ByteBuffer payload = packet.duplicate();
                    payload.position(cursor.payloadOffset);
                    payload.limit(Math.min(cursor.payloadOffset + cursor.payloadLength, payload.capacity()));
                    payload = payload.slice();
                    sni = SniExtractor.extract(payload);
                }

                packets.add(new PcapPacketInfo(
                        id++,
                        PcapPacketInfo.intToIp(cursor.srcIp),
                        PcapPacketInfo.intToIp(cursor.dstIp),
                        PcapPacketInfo.toUnsignedPort(cursor.srcPort),
                        PcapPacketInfo.toUnsignedPort(cursor.dstPort),
                        protocolStr,
                        sni,
                        packet.limit(),
                        blocked
                ));
            }
        }

        return packets;
    }
}
