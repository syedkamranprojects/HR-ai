import { useCallback, useEffect, useState } from 'react'

const BRAIN_URL = import.meta.env.VITE_BRAIN_URL || 'http://localhost:8080'

function sessionLink(id) {
  return `${window.location.origin}/?session=${id}`
}

export default function AdminPage() {
  const [sessions, setSessions] = useState([])
  const [name, setName] = useState('')
  const [age, setAge] = useState('')
  const [experience, setExperience] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)
  const [copiedId, setCopiedId] = useState(null)

  const loadSessions = useCallback(() => {
    fetch(`${BRAIN_URL}/api/admin/sessions`)
      .then((res) => res.json())
      .then(setSessions)
      .catch((err) => console.error(err))
  }, [])

  useEffect(() => {
    loadSessions()
  }, [loadSessions])

  const handleSubmit = useCallback(
    async (e) => {
      e.preventDefault()
      if (!name.trim()) return
      setSubmitting(true)
      setError(null)
      try {
        const res = await fetch(`${BRAIN_URL}/api/admin/sessions`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name: name.trim(), age: age.trim(), experience: experience.trim() }),
        })
        if (!res.ok) throw new Error(`Request failed (${res.status})`)
        setName('')
        setAge('')
        setExperience('')
        loadSessions()
      } catch (err) {
        setError('Could not create the session. Please try again.')
        console.error(err)
      } finally {
        setSubmitting(false)
      }
    },
    [name, age, experience, loadSessions],
  )

  const copyLink = useCallback((id) => {
    navigator.clipboard?.writeText(sessionLink(id)).then(() => {
      setCopiedId(id)
      setTimeout(() => setCopiedId(null), 1500)
    })
  }, [])

  return (
    <div className="admin-page">
      <div className="card admin-card">
        <h1>Configure an onboarding session</h1>
        <p className="subtitle">
          Set up a new employee's session ahead of their first day. You'll get a
          link to share with HR or the employee -- whoever starts the
          conversation, the assistant will already know who it's talking to.
        </p>

        {error && <div className="error-banner">{error}</div>}

        <form className="admin-form" onSubmit={handleSubmit}>
          <label>
            Employee name
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Ayesha Khan" required />
          </label>
          <label>
            Age <span className="optional">(optional)</span>
            <input value={age} onChange={(e) => setAge(e.target.value)} placeholder="e.g. 27" />
          </label>
          <label>
            Role / experience <span className="optional">(optional)</span>
            <input
              value={experience}
              onChange={(e) => setExperience(e.target.value)}
              placeholder="e.g. Senior Consultant, 5 years in insurance systems"
            />
          </label>
          <button type="submit" className="start-button admin-submit" disabled={submitting || !name.trim()}>
            {submitting ? 'Creating…' : 'Create Session'}
          </button>
        </form>
      </div>

      <div className="card admin-card">
        <h1>Configured sessions</h1>
        {sessions.length === 0 ? (
          <p className="subtitle">No sessions yet.</p>
        ) : (
          <ul className="session-list">
            {sessions.map((s) => (
              <li key={s.id} className="session-row">
                <div>
                  <div className="session-name">{s.name}</div>
                  <div className="session-meta">
                    {[s.age, s.experience].filter(Boolean).join(' · ') || 'No extra details'}
                  </div>
                </div>
                <button className="copy-button" onClick={() => copyLink(s.id)}>
                  {copiedId === s.id ? 'Copied!' : 'Copy link'}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
