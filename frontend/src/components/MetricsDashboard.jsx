import { useEffect, useRef, useState } from 'react'
import {
  PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend
} from 'recharts'

function useCountUp(target, duration = 1200) {
  const [value, setValue] = useState(0)
  const raf = useRef(null)

  useEffect(() => {
    if (target == null) {
      setValue(0)
      return
    }

    const start = performance.now()
    const from = 0
    const step = now => {
      const progress = Math.min((now - start) / duration, 1)
      const eased = 1 - Math.pow(1 - progress, 3)
      setValue(Math.round(from + (target - from) * eased))
      if (progress < 1) raf.current = requestAnimationFrame(step)
    }

    raf.current = requestAnimationFrame(step)
    return () => cancelAnimationFrame(raf.current)
  }, [target, duration])

  return value
}

const COLORS = ['#ef4444', '#f59e0b', '#a855f7', '#00e5ff', '#22c55e', '#ec4899']

const CustomTooltip = ({ active, payload }) => {
  if (!active || !payload?.length) return null
  return (
    <div style={{
      background: 'rgba(5,10,20,0.95)',
      border: '1px solid rgba(255,255,255,0.1)',
      borderRadius: '8px',
      padding: '10px 14px',
      fontSize: '0.78rem',
      color: '#f0f6ff'
    }}>
      <strong>{payload[0].name}</strong>
      <br />
      <span style={{ color: 'var(--text-2)' }}>{payload[0].value} packets</span>
    </div>
  )
}

function EmptyMetricsState() {
  const items = [
    ['Throughput', 'Run the engine', 'PPS appears here'],
    ['Total Packets', 'Awaiting run', 'Capture volume'],
    ['Forwarded', 'Awaiting run', 'Allowed packets'],
    ['Dropped', 'Awaiting run', 'Blocked packets']
  ]

  return (
    <div className="metrics-grid">
      {items.map(([label, value, sub]) => (
        <div key={label} className="card metric-card metric-card-empty">
          <p className="metric-label">{label}</p>
          <p className="metric-value metric-value-empty">{value}</p>
          <p className="metric-sub">{sub}</p>
        </div>
      ))}
    </div>
  )
}

export default function MetricsDashboard({ metrics }) {
  const pps = useCountUp(metrics?.pps, 900)
  const total = useCountUp(metrics?.totalPackets, 850)
  const forwarded = useCountUp(metrics?.forwarded, 800)
  const dropped = useCountUp(metrics?.dropped, 800)

  const pieData = metrics?.blockedDomains
    ? Object.entries(metrics.blockedDomains).map(([name, value]) => ({ name, value }))
    : []

  const durationPct = metrics ? Math.min((metrics.durationMs / 2000) * 100, 100) : 0

  if (!metrics) {
    return <EmptyMetricsState />
  }

  return (
    <>
      <div className="metrics-grid">
        <div className="card metric-card pps">
          <p className="metric-label">Throughput</p>
          <p className="metric-value">{pps.toLocaleString()}</p>
          <p className="metric-sub">packets / second</p>
        </div>
        <div className="card metric-card total">
          <p className="metric-label">Total Packets</p>
          <p className="metric-value">{total.toLocaleString()}</p>
          <p className="metric-sub">processed in run</p>
        </div>
        <div className="card metric-card fwd">
          <p className="metric-label">Forwarded</p>
          <p className="metric-value">{forwarded.toLocaleString()}</p>
          <p className="metric-sub">packets allowed</p>
        </div>
        <div className="card metric-card dropped">
          <p className="metric-label">Dropped</p>
          <p className="metric-value">{dropped.toLocaleString()}</p>
          <p className="metric-sub">packets blocked</p>
        </div>
      </div>

      <div className="charts-row">
        <div className="card chart-card">
          <p className="chart-card-title">Execution Time</p>
          <div className="duration-block">
            <span className="duration-label">Wall clock</span>
            <div className="duration-bar-track">
              <div className="duration-bar-fill" style={{ width: `${durationPct}%` }} />
            </div>
            <span className="duration-ms">{metrics.durationMs} ms</span>
          </div>
          <div style={{ padding: '0.5rem 1.5rem 1rem', fontSize: '0.75rem', color: 'var(--text-2)', lineHeight: 1.8 }}>
            <div>Workers: <span style={{ color: 'var(--cyan)', fontFamily: 'var(--mono)' }}>{metrics.workers} threads</span></div>
            <div>Mode: <span style={{ color: 'var(--violet)', fontFamily: 'var(--mono)' }}>Lock-free flow affinity</span></div>
            <div>Timing: <span style={{ color: 'var(--green)', fontFamily: 'var(--mono)' }}>Measured with nanosecond-resolution JVM clock</span></div>
          </div>
        </div>

        <div className="card chart-card">
          <p className="chart-card-title">Blocked Domain Breakdown</p>
          {pieData.length > 0 ? (
            <ResponsiveContainer width="100%" height={180}>
              <PieChart>
                <Pie
                  data={pieData}
                  dataKey="value"
                  nameKey="name"
                  cx="50%"
                  cy="50%"
                  innerRadius={40}
                  outerRadius={72}
                  paddingAngle={3}
                >
                  {pieData.map((_, i) => (
                    <Cell key={i} fill={COLORS[i % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip content={<CustomTooltip />} />
                <Legend
                  iconType="circle"
                  iconSize={8}
                  formatter={value => (
                    <span style={{ fontSize: '0.7rem', color: 'var(--text-2)' }}>{value}</span>
                  )}
                />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <p style={{ color: 'var(--text-3)', fontSize: '0.8rem', textAlign: 'center', padding: '2rem 0' }}>
              No blocked domains detected
            </p>
          )}
        </div>
      </div>
    </>
  )
}
