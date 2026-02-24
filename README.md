# Enterprise DPI Engine (Java)

A high-performance Deep Packet Inspection (DPI) system designed in Java. This project demonstrates advanced networking, non-blocking I/O, and low-latency concurrency principles by parsing network packets and performing Server Name Indication (SNI) filtering at hundreds of thousands of packets per second.

---

## 🚀 Key Architectural Features

Instead of relying on standard object creation, this engine implements several advanced JVM optimization techniques:

1. **Zero-Allocation Hot Path**: Uses the Flyweight Pattern with a `PacketCursor` over `ByteBuffer` instances. Memory is never allocated per-packet (no `new Object()`), entirely eliminating Garbage Collection (GC) pauses during parsing.
2. **Zero-Copy I/O**: Reads PCAP files directly into virtual memory using `FileChannel.map` and Memory Mapped Files. 
3. **Lock-Free Concurrency**: Implements a Load Balancer (Dispatcher) using Consistent Hashing on the network 5-Tuple (Src IP, Dst IP, Src Port, Dst Port, Protocol). This guarantees **flow affinity**—packets from the same connection always route to the exact same thread, removing the need for `ConcurrentHashMap` or `synchronized` blocks.
4. **TCP Stream Reassembly**: Properly handles out-of-order TCP segment delivery using a bounded `TreeMap` within thread-local context, allowing accurate SNI extraction even when the TLS Client Hello spans multiple packets.

---

## 📂 Project Structure

```text
com.enterprise.dpi
├── core/
│   ├── DpiEngine.java           # App bootstrapper & Thread Orchestrator
│   ├── PacketDispatcher.java    # Consistent hashing load balancer
│   └── FastPathWorker.java      # Lock-free worker threads
├── model/
│   ├── PacketCursor.java        # Flyweight zero-allocation pointer
│   └── FlowState.java           # TCP Stream state machine
├── net/
│   ├── PcapReader.java          # Zero-Copy mapped file reader
│   ├── PcapWriter.java          # Reassembles filtered PCAP
│   ├── ProtocolParser.java      # Extracts Ethernet, IP, TCP/UDP boundaries
│   └── TcpReassembler.java      # Handles out-of-order sequencing
├── inspection/
│   └── SniExtractor.java        # Reads TLS Client Hello
└── rules/
    └── RuleManager.java         # Domain blocklist
```

---

## 🛠️ Building and Running

You can compile this project using standard JDK tools (Java 17+ recommended) or Maven.

### Option 1: Using pure `javac` (No Maven required)

**Compile:**
```bash
# From the project root
mkdir -p bin
javac -d bin $(find src -name "*.java")
```
*(On Windows PowerShell, use `javac -d bin (Get-ChildItem -Recurse -Filter *.java).FullName`)*

**Run:**
```bash
# java -cp bin com.enterprise.dpi.core.DpiEngine <input.pcap> <output.pcap>
java -cp bin com.enterprise.dpi.core.DpiEngine test_dpi.pcap filtered_output.pcap
```

### Option 2: Using Maven

**Build a runnable jar:**
```bash
mvn clean package
```

**Run:**
```bash
java -jar target/dpi-engine-1.0-SNAPSHOT.jar test_dpi.pcap filtered_output.pcap
```

---

## 📊 Benchmarking & Performance Output

When run, the engine spawns worker threads equal to `CPU Cores - 1` and provides a summary.

```text
Starting Enterprise DPI Engine in Java
Spawning 11 FastPath workers
Finished reading PCAP. Waiting for workers to drain lines...

----------------- BENCHMARK -----------------
Processed 2,548,931 packets in 4102 ms
Throughput: 621,387 PPS (Packets Per Second)
Packets Forwarded: 2,501,100
Packets Dropped: 47,831
---------------------------------------------
```

---

## 🔍 How it Works (The Packet Journey)

1. **Ingestion**: The `PcapReader` memory-maps the `.pcap` file and yields slices of `ByteBuffer` representing raw packet data.
2. **Dispatch**: The `PacketDispatcher` does a shallow parse of the IP/TCP headers to fetch the 5-Tuple, hashes it, and routes the packet to a `FastPathWorker` queue.
3. **Parsing**: The `FastPathWorker` claims the packet and runs `ProtocolParser.parse()` using a lightweight `PacketCursor`.
4. **Reassembly**: The `TcpReassembler` aligns the packet's TCP Sequence numbers. If a packet arrives early, it buffers it in a `TreeMap` until the missing piece arrives.
5. **Inspection**: Once the TCP stream has enough bytes to form a TLS Handshake, `SniExtractor` digs into the payload, skipping Ciphers and Session IDs, to read the Server Name string.
6. **Filtering**: `RuleManager` checks if the SNI domain is blocked (e.g., `youtube.com`).
7. **Writing**: Allowed packets are passed to `PcapWriter`, which reconstructs a valid output PCAP file.

---

## 🎯 Profiling

This application is designed specifically to shine under Java profilers. 
Try running it with **Java Flight Recorder (JFR)**:
```bash
java -XX:StartFlightRecording=duration=30s,filename=profile.jfr -cp bin com.enterprise.dpi.core.DpiEngine huge_capture.pcap out.pcap
```
You will notice the GC allocation rate is incredibly low compared to standard Java applications handling identical workloads!

---

## 👨‍💻 Author

**Vidit Pandey**
- GitHub: [@viditpandey06](https://github.com/viditpandey06)

---

## ⚖️ License

**Copyright (c) 2026 Vidit Pandey. All Rights Reserved.**

This project is hosted on GitHub for portfolio and demonstration purposes only. You may view and read the source code. However, you are **NOT** granted permission to download, copy, modify, distribute, or use this software (or any portion of it) for personal, educational, or commercial purposes without explicit, written permission from the author.
