import { useEffect, useMemo, useState } from 'react'
import './index.css'
import PacketTable from './components/PacketTable'
import MetricsDashboard from './components/MetricsDashboard'

const API = import.meta.env.VITE_API_URL || 'http://localhost:8080'
const FALLBACK_PACKETS_URL = '/demo-packets.json'

const SERVICE_PRESETS = [
  {
    id: 'facebook',
    label: 'Facebook',
    description: 'Block Meta social traffic and auth handshakes.',
    domains: ['facebook.com']
  },
  {
    id: 'tiktok',
    label: 'TikTok',
    description: 'Simulate policy drops for TikTok sessions.',
    domains: ['tiktok.com']
  },
  {
    id: 'youtube',
    label: 'YouTube',
    description: 'Catch video and site traffic under YouTube.',
    domains: ['youtube.com', 'googlevideo.com', 'ytimg.com']
  },
  {
    id: 'google',
    label: 'Google',
    description: 'Block Google web traffic in the same run.',
    domains: ['google.com', 'gstatic.com']
  }
]

export default function App() {
  const [packets, setPackets] = useState([])
  const [metrics, setMetrics] = useState(null)
  const [running, setRunning] = useState(false)
  const [waking, setWaking] = useState(false)
  const [loading, setLoading] = useState(true)
  const [lastRun, setLastRun] = useState(null)
  const [lastRunSummary, setLastRunSummary] = useState(null)
  const [selectedServices, setSelectedServices] = useState(['tiktok', 'facebook'])
  const [appliedServices, setAppliedServices] = useState([])
  const [appliedBlockedDomains, setAppliedBlockedDomains] = useState([])
  const [backendReady, setBackendReady] = useState(false)
  const [usingFallbackPackets, setUsingFallbackPackets] = useState(false)
  const [livePacketsLoaded, setLivePacketsLoaded] = useState(false)

  const selectedServiceDetails = useMemo(
    () => SERVICE_PRESETS.filter(({ id }) => selectedServices.includes(id)),
    [selectedServices]
  )

  const blockedDomains = useMemo(
    () => selectedServiceDetails.flatMap(({ domains }) => domains),
    [selectedServiceDetails]
  )

  const appliedServiceLabels = useMemo(() => {
    return SERVICE_PRESETS
      .filter(({ id }) => appliedServices.includes(id))
      .map(({ label }) => label)
  }, [appliedServices])

  const selectedServiceLabels = useMemo(() => {
    return SERVICE_PRESETS
      .filter(({ id }) => selectedServices.includes(id))
      .map(({ label }) => label)
  }, [selectedServices])

  useEffect(() => {
    let cancelled = false

    const loadFallbackPackets = async () => {
      try {
        const response = await fetch(FALLBACK_PACKETS_URL)
        if (!response.ok) {
          throw new Error('Fallback packets unavailable')
        }

        const data = await response.json()
        if (!cancelled) {
          setPackets(data)
          setUsingFallbackPackets(true)
          setLoading(false)
        }
      } catch {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    const loadLivePackets = async () => {
      try {
        const response = await fetch(`${API}/api/packets`)
        if (!response.ok) {
          throw new Error('Backend packets unavailable')
        }

        const data = await response.json()
        if (!cancelled) {
          setPackets(data)
          setUsingFallbackPackets(false)
          setBackendReady(true)
          setLivePacketsLoaded(true)
          setLoading(false)
        }
        return true
      } catch {
        if (!cancelled) {
          setBackendReady(false)
        }
        return false
      }
    }

    loadFallbackPackets()
    loadLivePackets()

    const intervalId = window.setInterval(async () => {
      try {
        const response = await fetch(`${API}/health`)
        if (!response.ok) {
          throw new Error('Health check failed')
        }

        if (!cancelled) {
          setBackendReady(true)
        }

        if (!livePacketsLoaded) {
          await loadLivePackets()
        }
      } catch {
        if (!cancelled) {
          setBackendReady(false)
        }
      }
    }, 8000)

    return () => {
      cancelled = true
      window.clearInterval(intervalId)
    }
  }, [livePacketsLoaded])


  const toggleService = serviceId => {
    setSelectedServices(current =>
      current.includes(serviceId)
        ? current.filter(id => id !== serviceId)
        : [...current, serviceId]
    )
  }

  const handleRun = async () => {
    if (!backendReady) {
      return
    }

    setRunning(true)
    const wakeTimer = setTimeout(() => setWaking(true), 3000)

    try {
      const response = await fetch(`${API}/api/run`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ blockedDomains })
      })

      const data = await response.json()
      clearTimeout(wakeTimer)
      setWaking(false)
      setMetrics(data)
      setAppliedBlockedDomains(blockedDomains)
      setAppliedServices(selectedServices)
      setLastRun(new Date().toLocaleTimeString())
      setLastRunSummary({
        durationMs: data.durationMs,
        totalPackets: data.totalPackets,
        blockedPackets: data.dropped
      })
    } catch {
      clearTimeout(wakeTimer)
      setWaking(false)
      setBackendReady(false)
    } finally {
      setRunning(false)
    }
  }

  const runButtonLabel = !backendReady
    ? 'Warming Up Engine'
    : 'Run DPI Engine'

  return (
    <div className="app">
      <header className="header">
        <div>
          <h1 className="header-title">
            Enterprise <span>DPI Engine</span>
          </h1>
          <p style={{ color: 'var(--text-2)', fontSize: '0.82rem', marginTop: '0.2rem' }}>
            Zero-allocation | Lock-free | 1.5M+ PPS
          </p>
        </div>
        <div className="header-badges">
          <a
            className="developer-link"
            href="https://viditpandey.in"
            target="_blank"
            rel="noopener"
            aria-label="Open developer portfolio"
            title="Developer Portfolio"
          >
            <span className="developer-link-icon">{'</>'}</span>
            <span>Developer</span>
          </a>
          <span className="badge badge-cyan"><span className="badge-dot" />Live Demo</span>
          <span className="badge badge-violet">Java 17</span>
          <span className="badge badge-green">Zero GC</span>
        </div>
      </header>

      <section className="run-section">
        <p className="section-title">Engine Configuration</p>
        <div className="card" style={{ padding: '2rem' }}>
          <div style={{ marginBottom: '2rem' }}>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-2)', marginBottom: '1rem' }}>
              Select a policy, then run the engine. The Java DPI pipeline reads the packet stream, extracts TLS SNI values, and applies the selected rules during execution.
            </p>

            <div className="service-grid">
              {SERVICE_PRESETS.map(service => {
                const isActive = selectedServices.includes(service.id)

                return (
                  <button
                    key={service.id}
                    type="button"
                    className={`service-chip${isActive ? ' active' : ''}`}
                    onClick={() => toggleService(service.id)}
                  >
                    <div className="service-chip-top">
                      <div>
                        <div className="service-chip-name">{service.label}</div>
                        <div className="service-chip-domain">{service.domains.join(', ')}</div>
                      </div>
                    </div>

                    <p className="service-chip-description">{service.description}</p>
                    <div className={`service-chip-status${isActive ? ' service-chip-status-active' : ''}`}>
                      {isActive ? 'Selected for the next engine run' : 'Click to include in the next run'}
                    </div>
                  </button>
                )
              })}
            </div>

            <div className="policy-summary">
              <div>
                <strong>Run-time policy:</strong> the engine will evaluate the packet stream against the selected domains on the next run
              </div>
              <div className="policy-note">
                Execution time is measured by the Java engine at run time with nanosecond-resolution timing before being reported in the dashboard.
              </div>
            </div>

            <div className="policy-summary" style={{ marginTop: '0.75rem' }}>
              <div>
                <strong>Active policy:</strong> {selectedServiceLabels.length > 0 ? selectedServiceLabels.join(', ') : 'No policy selected'}
              </div>
              <div className="policy-note">
                {lastRun
                  ? `Last engine run applied ${appliedServiceLabels.length > 0 ? appliedServiceLabels.join(', ') : 'no blocking policy'} at ${lastRun}.`
                  : 'Update the selection, then run the engine to apply it to the viewer and metrics.'}
              </div>
            </div>

            {lastRunSummary && (
              <div className="last-run-line">
                Last run: {lastRunSummary.durationMs} ms · {lastRunSummary.totalPackets.toLocaleString()} packets · {lastRunSummary.blockedPackets.toLocaleString()} blocked
              </div>
            )}
          </div>

          {!backendReady && (
            <div className="warming-banner">
              Render free tier backend is waking up. The packet list is ready from the bundled snapshot, and the engine run button will enable as soon as the server responds.
            </div>
          )}

          {usingFallbackPackets && (
            <div className="snapshot-note">
              Viewer loaded from the bundled packet snapshot while the backend initializes.
            </div>
          )}

          <div className="run-button-wrap" style={{ padding: 0 }}>
            {running ? (
              <div className="spinner-wrap">
                <div className="spinner" />
                <p className="spinner-label">
                  {waking
                    ? 'Waking up Render server... (~30s first run)'
                    : 'Applying the selected blocking policy...'}
                </p>
              </div>
            ) : (
              <>
                <button
                  className={`run-button${!backendReady ? ' run-button-disabled' : ''}`}
                  onClick={handleRun}
                  disabled={!backendReady}
                >
                  {runButtonLabel}
                </button>
                <p className="run-button-hint">
                  {!backendReady
                    ? 'Render free tier may take a short while to spin the backend back up. The control will enable automatically.'
                    : 'Runs the engine with the currently selected services and then refreshes the viewer using the applied policy.'}
                  {lastRun && <><br /><strong>Last run: {lastRun}</strong></>}
                </p>
              </>
            )}
          </div>
        </div>
      </section>

      {(metrics || !loading) && (
        <section style={{ marginBottom: '2rem' }}>
          <p className="section-title">Benchmark Results</p>
          <MetricsDashboard metrics={metrics} />
        </section>
      )}

      <section>
        <p className="section-title">PCAP Viewer - demo.pcap</p>
        <PacketTable packets={packets} loading={loading} dynamicBlockedDomains={appliedBlockedDomains} />
      </section>

      <footer className="footer">
        <span className="footer-name">Vidit Pandey</span>
        <div className="footer-links">
          <a href="https://github.com/viditpandey06" target="_blank" rel="noopener">GitHub</a>
          <a href="https://github.com/viditpandey06/java-zero-allocation-dpi" target="_blank" rel="noopener">Source Code</a>
          <a href="https://viditpandey.in" target="_blank" rel="noopener">Portfolio</a>
        </div>
      </footer>
    </div>
  )
}
