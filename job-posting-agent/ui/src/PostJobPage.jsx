import { useCallback, useState } from 'react'

const JOBS_URL = import.meta.env.VITE_JOBS_URL || 'http://localhost:8081'

function postingAsText(posting) {
  const lines = [
    posting.title,
    '',
    posting.summary,
    '',
    `Employment type: ${posting.employmentType || '-'}`,
    `Location: ${posting.location || '-'}`,
    '',
    'Must-have requirements:',
    ...posting.mustHaveRequirements.map((r) => `- ${r}`),
  ]
  if (posting.preferredCriteria.length > 0) {
    lines.push('', 'Preferred:', ...posting.preferredCriteria.map((r) => `- ${r}`))
  }
  if (posting.educationRequirements.length > 0) {
    lines.push('', 'Education:', ...posting.educationRequirements.map((r) => `- ${r}`))
  }
  return lines.join('\n')
}

export default function PostJobPage() {
  const [prompt, setPrompt] = useState('')
  const [drafts, setDrafts] = useState([])
  const [expandedId, setExpandedId] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)
  const [copiedId, setCopiedId] = useState(null)

  const handleSubmit = useCallback(
    async (e) => {
      e.preventDefault()
      if (!prompt.trim()) return
      setSubmitting(true)
      setError(null)
      try {
        const res = await fetch(`${JOBS_URL}/v1/draft-posting`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ prompt: prompt.trim() }),
        })
        if (!res.ok) throw new Error(`Request failed (${res.status})`)
        const posting = await res.json()
        const id = crypto.randomUUID()
        setDrafts((prev) => [{ id, posting }, ...prev])
        setExpandedId(id)
        setPrompt('')
      } catch (err) {
        setError('Could not draft a posting. Please try again.')
        console.error(err)
      } finally {
        setSubmitting(false)
      }
    },
    [prompt],
  )

  const copyText = useCallback((id, posting) => {
    navigator.clipboard?.writeText(postingAsText(posting)).then(() => {
      setCopiedId(id)
      setTimeout(() => setCopiedId(null), 1500)
    })
  }, [])

  const removeDraft = useCallback((id) => {
    setDrafts((prev) => prev.filter((d) => d.id !== id))
    setExpandedId((prev) => (prev === id ? null : prev))
  }, [])

  return (
    <div className="admin-page">
      <div className="card admin-card">
        <h1>Draft a job posting</h1>
        <p className="subtitle">
          Describe the role in your own words -- skills needed, seniority, location, anything
          that matters. The assistant drafts a structured posting for you to review and edit
          before posting it to LinkedIn or the careers page yourself; it never posts anything on
          its own.
        </p>

        {error && <div className="error-banner">{error}</div>}

        <form className="admin-form" onSubmit={handleSubmit}>
          <label>
            Role description
            <textarea
              className="posting-prompt"
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="e.g. We need a mid-level backend engineer who knows Java and Spring Boot, has worked with Postgres, and ideally has some AWS experience. Remote-friendly, full-time."
              rows={4}
              required
            />
          </label>
          <button type="submit" className="start-button admin-submit" disabled={submitting || !prompt.trim()}>
            {submitting ? 'Drafting…' : 'Draft Posting'}
          </button>
        </form>
      </div>

      <div className="card admin-card">
        <h1>Drafted postings</h1>
        {drafts.length === 0 ? (
          <p className="subtitle">No postings drafted yet this session.</p>
        ) : (
          <ul className="session-list">
            {drafts.map(({ id, posting }) => (
              <li key={id} className="posting-item">
                <div className="session-row">
                  <div>
                    <div className="session-name">{posting.title}</div>
                    <div className="session-meta">
                      {[posting.employmentType, posting.location].filter(Boolean).join(' · ') || 'No extra details'}
                    </div>
                  </div>
                  <div className="posting-actions">
                    <button
                      className="copy-button"
                      onClick={() => setExpandedId((prev) => (prev === id ? null : id))}
                    >
                      {expandedId === id ? 'Hide' : 'View'}
                    </button>
                    <button className="copy-button" onClick={() => copyText(id, posting)}>
                      {copiedId === id ? 'Copied!' : 'Copy text'}
                    </button>
                    <button className="remove-button" onClick={() => removeDraft(id)} aria-label="Remove draft">
                      ×
                    </button>
                  </div>
                </div>

                {expandedId === id && (
                  <div className="posting-detail">
                    <p>{posting.summary}</p>

                    <div className="posting-section">
                      <h3>Must-have requirements</h3>
                      <ul>
                        {posting.mustHaveRequirements.map((r, i) => (
                          <li key={i}>{r}</li>
                        ))}
                      </ul>
                    </div>

                    {posting.preferredCriteria.length > 0 && (
                      <div className="posting-section">
                        <h3>Preferred</h3>
                        <ul>
                          {posting.preferredCriteria.map((r, i) => (
                            <li key={i}>{r}</li>
                          ))}
                        </ul>
                      </div>
                    )}

                    {posting.educationRequirements.length > 0 && (
                      <div className="posting-section">
                        <h3>Education</h3>
                        <ul>
                          {posting.educationRequirements.map((r, i) => (
                            <li key={i}>{r}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
