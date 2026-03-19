import { useMemo, useState } from 'react'

const PROTO_COLOR = { TCP: '#00e5ff', UDP: '#f59e0b', OTHER: '#8fa3bf' }

function matchesBlockedDomain(sni, blockedDomains) {
  if (!sni) {
    return false
  }

  const normalizedSni = sni.toLowerCase()
  return blockedDomains.some(domain => {
    const normalizedDomain = domain.toLowerCase()
    return normalizedSni === normalizedDomain || normalizedSni.endsWith(`.${normalizedDomain}`)
  })
}

function packetMatchesQuery(packet, rawQuery) {
  const query = rawQuery.trim().toLowerCase()
  if (!query) {
    return true
  }

  const source = `${packet.srcIp || ''}:${packet.srcPort || ''}`.toLowerCase()
  const destination = `${packet.dstIp || ''}:${packet.dstPort || ''}`.toLowerCase()
  const srcIp = (packet.srcIp || '').toLowerCase()
  const dstIp = (packet.dstIp || '').toLowerCase()
  const srcPort = String(packet.srcPort || '')
  const dstPort = String(packet.dstPort || '')
  const sni = (packet.sni || '').toLowerCase()
  const protocol = (packet.protocol || '').toLowerCase()
  const status = packet.isBlocked ? 'blocked' : 'allow'
  const id = String(packet.id || '')

  const matchesQualified = (prefixes, value) => {
    const matchedPrefix = prefixes.find(prefix => query.startsWith(prefix))
    if (!matchedPrefix) {
      return false
    }

    const needle = query.slice(matchedPrefix.length).trim()
    return needle.length > 0 && value.includes(needle)
  }

  if (matchesQualified(['src:', 'source:'], source)) return true
  if (matchesQualified(['dst:', 'destination:'], destination)) return true
  if (matchesQualified(['sni:'], sni)) return true
  if (matchesQualified(['proto:', 'protocol:'], protocol)) return true
  if (matchesQualified(['port:'], `${srcPort} ${dstPort}`)) return true
  if (matchesQualified(['status:'], status)) return true
  if (matchesQualified(['id:'], id)) return true

  if (query.includes(':')) {
    return false
  }

  return (
    source.includes(query) ||
    destination.includes(query) ||
    srcIp.includes(query) ||
    dstIp.includes(query) ||
    srcPort.includes(query) ||
    dstPort.includes(query) ||
    sni.includes(query) ||
    protocol.includes(query) ||
    status.includes(query) ||
    id.includes(query)
  )
}

export default function PacketTable({ packets, loading, dynamicBlockedDomains }) {
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('all')

  const processedPackets = useMemo(() => {
    return packets.map(packet => ({
      ...packet,
      isBlocked: matchesBlockedDomain(packet.sni, dynamicBlockedDomains)
    }))
  }, [packets, dynamicBlockedDomains])

  const filtered = useMemo(() => {
    let list = processedPackets

    if (filter === 'allow') list = list.filter(packet => !packet.isBlocked)
    if (filter === 'block') list = list.filter(packet => packet.isBlocked)

    if (search.trim()) {
      list = list.filter(packet => packetMatchesQuery(packet, search))
    }

    return list
  }, [processedPackets, filter, search])

  return (
    <div className="card table-card">
      <div className="table-toolbar">
        <input
          type="text"
          className="table-search"
          placeholder="Search IP, port, SNI, protocol or use src:/dst:/sni:/port: ..."
          value={search}
          onChange={event => setSearch(event.target.value)}
        />
        <div className="table-toolbar-right">
          <div className="filter-pills">
            {[
              { key: 'all', label: `All (${processedPackets.length})`, cls: 'active-all' },
              { key: 'allow', label: `Allowed (${processedPackets.filter(packet => !packet.isBlocked).length})`, cls: 'active-allow' },
              { key: 'block', label: `Blocked (${processedPackets.filter(packet => packet.isBlocked).length})`, cls: 'active-block' }
            ].map(({ key, label, cls }) => (
              <button
                key={key}
                className={`pill ${filter === key ? cls : ''}`}
                onClick={() => setFilter(key)}
              >
                {label}
              </button>
            ))}
          </div>
          <div className="table-count-inline">
            Showing {Math.min(filtered.length, 500).toLocaleString()} of {filtered.length.toLocaleString()}
          </div>
        </div>
      </div>

      <div className="table-scroll">
        {loading ? (
          <div style={{ padding: '3rem', textAlign: 'center', color: 'var(--text-3)', fontSize: '0.85rem' }}>
            Loading packets...
          </div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>Protocol</th>
                <th>Source</th>
                <th>Destination</th>
                <th>SNI (Domain)</th>
                <th>Size</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {filtered.slice(0, 500).map(packet => (
                <tr key={packet.id}>
                  <td className="td-id">{packet.id}</td>
                  <td>
                    <span style={{
                      color: PROTO_COLOR[packet.protocol] || 'var(--text-2)',
                      fontWeight: 600,
                      fontSize: '0.72rem'
                    }}>
                      {packet.protocol}
                    </span>
                  </td>
                  <td>{packet.srcIp}:{packet.srcPort}</td>
                  <td>{packet.dstIp}:{packet.dstPort}</td>
                  <td className="td-sni-cell">
                    {packet.sni || '-'}
                  </td>
                  <td>{packet.length} B</td>
                  <td>
                    <span className={`status-dot ${packet.isBlocked ? 'status-block' : 'status-allow'}`}>
                      {packet.isBlocked ? 'Blocked' : 'Allow'}
                    </span>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={7} style={{ padding: '1.5rem', textAlign: 'center', color: 'var(--text-3)' }}>
                    No packets match your filter
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      <div className="table-count">
        {packets.length > 0 ? `${packets.length.toLocaleString()} total packets loaded from the PCAP` : 'No packets loaded'}
      </div>
    </div>
  )
}
