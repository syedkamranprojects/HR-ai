import { useCallback, useEffect, useRef, useState } from 'react'

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
  if (posting.applicationEmail) {
    lines.push('', `To apply, send your resume to ${posting.applicationEmail}.`)
  }
  return lines.join('\n')
}

// What the model itself said last turn, so it can refine its own prior
// output -- the applicationEmail is HR-supplied, not model-generated, so it
// stays out of the model's own conversation memory.
function draftAsHistoryContent(posting) {
  const { applicationEmail: _applicationEmail, ...draft } = posting
  return JSON.stringify(draft)
}

function PostingCard({ posting, onCopy, copied }) {
  return (
    <div className="posting-card">
      <div className="posting-card-header">
        <h3>{posting.title}</h3>
        <span className="posting-card-meta">
          {[posting.employmentType, posting.location].filter(Boolean).join(' · ')}
        </span>
      </div>
      <p>{posting.summary}</p>

      <div className="posting-section">
        <h4>Must-have requirements</h4>
        <ul>
          {posting.mustHaveRequirements.map((r, i) => (
            <li key={i}>{r}</li>
          ))}
        </ul>
      </div>

      {posting.preferredCriteria.length > 0 && (
        <div className="posting-section">
          <h4>Preferred</h4>
          <ul>
            {posting.preferredCriteria.map((r, i) => (
              <li key={i}>{r}</li>
            ))}
          </ul>
        </div>
      )}

      {posting.educationRequirements.length > 0 && (
        <div className="posting-section">
          <h4>Education</h4>
          <ul>
            {posting.educationRequirements.map((r, i) => (
              <li key={i}>{r}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="posting-section">
        <h4>Apply to</h4>
        {posting.applicationEmail ? (
          <p className="posting-email">{posting.applicationEmail}</p>
        ) : (
          <p className="posting-email-missing">
            No application email set -- add one below before posting this externally.
          </p>
        )}
      </div>

      <button className="copy-button posting-card-copy" onClick={onCopy}>
        {copied ? 'Copied!' : 'Copy as text'}
      </button>
    </div>
  )
}

export default function PostJobPage() {
  const [messages, setMessages] = useState([]) // { id, role: 'user'|'assistant', text?, posting? }
  const [draft, setDraft] = useState('')
  const [applicationEmail, setApplicationEmail] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState(null)
  const [copiedId, setCopiedId] = useState(null)
  const bottomRef = useRef(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length])

  const handleSend = useCallback(
    async (e) => {
      e.preventDefault()
      const text = draft.trim()
      if (!text || sending) return

      const userMessage = { id: crypto.randomUUID(), role: 'user', text }
      const nextMessages = [...messages, userMessage]
      setMessages(nextMessages)
      setDraft('')
      setSending(true)
      setError(null)

      try {
        const historyPayload = nextMessages.map((m) => ({
          role: m.role,
          content: m.role === 'user' ? m.text : draftAsHistoryContent(m.posting),
        }))
        const res = await fetch(`${JOBS_URL}/v1/draft-posting`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ messages: historyPayload, applicationEmail: applicationEmail.trim() || null }),
        })
        if (!res.ok) throw new Error(`Request failed (${res.status})`)
        const posting = await res.json()
        setMessages((prev) => [...prev, { id: crypto.randomUUID(), role: 'assistant', posting }])
      } catch (err) {
        setError(
          messages.length === 0
            ? 'Could not draft a posting. Please try again.'
            : 'Could not apply that change. Please try again.',
        )
        console.error(err)
      } finally {
        setSending(false)
      }
    },
    [draft, sending, messages, applicationEmail],
  )

  const copyText = useCallback((id, posting) => {
    navigator.clipboard?.writeText(postingAsText(posting)).then(() => {
      setCopiedId(id)
      setTimeout(() => setCopiedId(null), 1500)
    })
  }, [])

  const startOver = useCallback(() => {
    setMessages([])
    setDraft('')
    setError(null)
  }, [])

  return (
    <div className="card conversation-card">
      <div className="conversation-header">
        <div className="conversation-header-row">
          <h1>Draft a job posting</h1>
          {messages.length > 0 && (
            <button className="copy-button" onClick={startOver}>
              Start over
            </button>
          )}
        </div>
        {messages.length === 0 && (
          <p className="subtitle">
            Describe the role in your own words. You'll get a structured draft back, and can keep
            asking for changes -- "make it stricter", "drop the Docker requirement" -- until it's
            ready. HR always reviews and posts it manually; nothing here publishes on its own.
          </p>
        )}
      </div>

      {error && <div className="error-banner">{error}</div>}

      <div className="transcript">
        {messages.length === 0 ? (
          <div className="transcript-empty">Your conversation will appear here.</div>
        ) : (
          messages.map((m) => (
            <div key={m.id} className={`bubble-row ${m.role === 'user' ? 'local' : 'remote'}`}>
              {m.role === 'user' ? (
                <div className="bubble">{m.text}</div>
              ) : (
                <PostingCard
                  posting={m.posting}
                  onCopy={() => copyText(m.id, m.posting)}
                  copied={copiedId === m.id}
                />
              )}
            </div>
          ))
        )}
        {sending && (
          <div className="bubble-row remote">
            <div className="bubble bubble-thinking">Drafting…</div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      <label className="email-row">
        Application email <span className="optional">(optional, but recommended)</span>
        <input
          type="email"
          value={applicationEmail}
          onChange={(e) => setApplicationEmail(e.target.value)}
          placeholder="e.g. careers@centegytechnologies.com"
        />
      </label>

      <form className="chat-input-row" onSubmit={handleSend}>
        <input
          type="text"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={
            messages.length === 0
              ? 'e.g. Mid-level backend engineer, Java and Spring Boot, remote, full-time…'
              : 'Ask for a change…'
          }
          className="chat-input"
        />
        <button type="submit" className="send-button" disabled={!draft.trim() || sending}>
          Send
        </button>
      </form>
    </div>
  )
}
