# DPI Engine Portfolio Dashboard

A full-stack portfolio page showcasing the Enterprise DPI Engine. Visitors press a button to run the engine against a pre-bundled PCAP file and see live benchmark results plus a Wireshark-style packet viewer.

---

## Proposed Changes

### Backend (Java / Javalin)

#### [MODIFY] [pom.xml](file:///c:/Users/Vidit/Downloads/Packet_analyzer-main/pom.xml)
- Add **Javalin 6** dependency (lightweight HTTP server)
- Add **Jackson Databind** dependency (JSON serialization)
- Change the maven-shade `mainClass` from [DpiEngine](file:///c:/Users/Vidit/Downloads/Packet_analyzer-main/src/main/java/com/enterprise/dpi/core/DpiEngine.java#20-110) to `DpiServer`

#### [NEW] `src/main/resources/test_dpi.pcap`
- Copy the bundled [test_dpi.pcap](file:///c:/Users/Vidit/Downloads/Packet_analyzer-main/test_dpi.pcap) into Maven resources so it gets embedded inside the fat JAR and is accessible at runtime on Render without needing a separate file on disk.

#### [NEW] `src/main/java/com/enterprise/dpi/server/PcapPacketInfo.java`
- Simple POJO with fields: `srcIp`, `dstIp`, `srcPort`, `dstPort`, `protocol`, `sni`, `length`, `blocked`
- Jackson will serialize this to JSON.

#### [NEW] `src/main/java/com/enterprise/dpi/server/PcapPreParser.java`
- Reads the bundled PCAP on server startup using existing [PcapReader](file:///c:/Users/Vidit/Downloads/Packet_analyzer-main/src/main/java/com/enterprise/dpi/net/PcapReader.java#13-73) + [ProtocolParser](file:///c:/Users/Vidit/Downloads/Packet_analyzer-main/src/main/java/com/enterprise/dpi/net/ProtocolParser.java#10-94) + [SniExtractor](file:///c:/Users/Vidit/Downloads/Packet_analyzer-main/src/main/java/com/enterprise/dpi/inspection/SniExtractor.java#10-89) + [RuleManager](file:///c:/Users/Vidit/Downloads/Packet_analyzer-main/src/main/java/com/enterprise/dpi/rules/RuleManager.java#10-44).
- Builds a `List<PcapPacketInfo>` once and caches it in memory — served instantly on every `GET /api/packets` request.
- Limits output to **2000 packets** for frontend performance.

#### [NEW] `src/main/java/com/enterprise/dpi/server/DpiServer.java`
- The new [main()](file:///c:/Users/Vidit/Downloads/Packet_analyzer-main/src/main/java/com/enterprise/dpi/core/DpiEngine.java#22-109) entry point.
- Starts a **Javalin** HTTP server on `PORT` (env var, default 8080).
- Calls `PcapPreParser` on startup.
- **CORS**: Allows all origins (you can lock this to your Vercel URL later).
- **Endpoints**:
  - `GET /api/packets` → Returns JSON array of `PcapPacketInfo`
  - `POST /api/run` → Extracts the bundled PCAP to a temp file, runs [DpiEngine](file:///c:/Users/Vidit/Downloads/Packet_analyzer-main/src/main/java/com/enterprise/dpi/core/DpiEngine.java#20-110) logic (programmatically, not via shell), returns JSON:
    ```json
    {
      "totalPackets": 640077,
      "durationMs": 426,
      "pps": 1502528,
      "forwarded": 31022,
      "dropped": 0
    }
    ```
  - `GET /health` → Returns `200 OK` for Render health checks

#### [NEW] `render.yaml`
```yaml
services:
  - type: web
    name: dpi-engine-api
    runtime: java
    buildCommand: mvn clean package -DskipTests
    startCommand: java -jar target/dpi-engine-1.0-SNAPSHOT.jar
    envVars:
      - key: PORT
        value: 8080
```

---

### Frontend (React / Vite)

Lives in a `/frontend` subdirectory of the project.

#### [NEW] `frontend/` — Vite React app
Initialized via `npx create-vite@latest . --template react`.

**Key components:**
- **`Header`** — Project title, GitHub link, "last run" timestamp
- **`PacketTable`** — Virtualized scrollable table of up to 2000 packets showing: `#`, `Protocol`, `Src IP:Port → Dst IP:Port`, `SNI`, `Size`, `Status` (Allowed 🟢 / Blocked 🔴)
- **`RunEngineButton`** — Prominent CTA. On click, shows a **cold-start preloader** (spinny animation with "Waking up Render server…" message if the first ping takes >3s) before triggering `POST /api/run`
- **`MetricsDashboard`** — Animated number counters for PPS, Total Packets, Forwarded, Dropped + a domain pie-chart breakdown (using [Recharts](https://recharts.org/))

**Design:** Dark-mode glassmorphism. Deep dark `#0a0f1e` background, cyan/violet gradient accents, card blur effects, smooth counter animations.

**Environment variable:** `VITE_API_URL` (set on Vercel to your Render URL)

#### [NEW] `frontend/vercel.json`
```json
{
  "rewrites": [{ "source": "/(.*)", "destination": "/index.html" }]
}
```

---

## Verification Plan

### Automated Tests
- No new unit tests (existing engine logic is unchanged and already verified).
- Maven build test confirms no compile errors:
  ```powershell
  cd c:\Users\Vidit\Downloads\Packet_analyzer-main
  mvn clean package -DskipTests
  ```

### Manual Verification

**Backend:**
```powershell
# From project root
java -jar target/dpi-engine-1.0-SNAPSHOT.jar
# Then in a browser or curl:
curl http://localhost:8080/api/packets   # Should return JSON array
curl -X POST http://localhost:8080/api/run  # Should return benchmark JSON
```

**Frontend:**
```powershell
cd frontend
npm install
npm run dev
# Open http://localhost:5173 in browser
# Verify: packet table loads, "Run Engine" button triggers loading state, metrics animate in
```
