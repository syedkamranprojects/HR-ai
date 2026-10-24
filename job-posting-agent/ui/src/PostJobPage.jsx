import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

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

function ScoreCard({ entry }) {
  return (
    <div className="score-card">
      <div className="score-card-header">
        <h3>{entry.fileName}</h3>
        {entry.status === 'done' && <span className="score-badge">{entry.score.fitScore}/100</span>}
      </div>

      {entry.status === 'scoring' && <p className="subtitle score-status">Scoring…</p>}
      {entry.status === 'error' && <div className="error-banner">{entry.error}</div>}

      {entry.status === 'done' && (
        <>
          <p className="score-candidate-name">{entry.score.candidateName}</p>
          <p>{entry.score.summary}</p>

          <div className="posting-section">
            <h4>Must-have: matched</h4>
            {entry.score.matchedMustHave.length > 0 ? (
              <ul>
                {entry.score.matchedMustHave.map((r, i) => (
                  <li key={i}>{r}</li>
                ))}
              </ul>
            ) : (
              <p className="score-none">None</p>
            )}
          </div>

          {entry.score.missingMustHave.length > 0 && (
            <div className="posting-section">
              <h4>Must-have: missing</h4>
              <ul>
                {entry.score.missingMustHave.map((r, i) => (
                  <li key={i}>{r}</li>
                ))}
              </ul>
            </div>
          )}

          {entry.score.matchedPreferred.length > 0 && (
            <div className="posting-section">
              <h4>Preferred: matched</h4>
              <ul>
                {entry.score.matchedPreferred.map((r, i) => (
                  <li key={i}>{r}</li>
                ))}
              </ul>
            </div>
          )}

          {entry.score.missingPreferred.length > 0 && (
            <div className="posting-section">
              <h4>Preferred: missing</h4>
              <ul>
                {entry.score.missingPreferred.map((r, i) => (
                  <li key={i}>{r}</li>
                ))}
              </ul>
            </div>
          )}

          <div className="posting-section">
            <h4>For HR</h4>
            <p className="score-recommendation">{entry.score.recommendation}</p>
          </div>
        </>
      )}
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
  const [candidateScores, setCandidateScores] = useState([]) // { id, fileName, status, score?, error? }
  const [openedPosting, setOpenedPosting] = useState(null) // { id, inboxDir } for the posting currently shown
  const [opening, setOpening] = useState(false)
  const bottomRef = useRef(null)
  const resumeInputRef = useRef(null)

  const latestMessageId = [...messages].reverse().find((m) => m.role === 'assistant')?.id ?? null
  const latestPosting = messages.find((m) => m.id === latestMessageId)?.posting ?? null

  const handleOpenForApplications = useCallback(async () => {
    if (!latestPosting || opening) return
    setOpening(true)
    setError(null)
    try {
      const res = await fetch(`${JOBS_URL}/v1/postings`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(latestPosting),
      })
      if (!res.ok) throw new Error(`Request failed (${res.status})`)
      const posting = await res.json()
      setOpenedPosting({ id: posting.id, inboxDir: posting.inboxDir, forMessageId: latestMessageId })
    } catch (err) {
      setError('Could not open this posting for applications. Please try again.')
      console.error(err)
    } finally {
      setOpening(false)
    }
  }, [latestPosting, latestMessageId, opening])

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
    const text = postingAsText(posting)
    const markCopied = () => {
      setCopiedId(id)
      setTimeout(() => setCopiedId(null), 1500)
    }
    // navigator.clipboard can reject (e.g. permission denied, no recent user
    // gesture) with no visible symptom other than the button never updating
    // -- fall back to the older execCommand approach rather than fail silently.
    const fallback = () => {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.focus()
      textarea.select()
      try {
        if (document.execCommand('copy')) markCopied()
      } catch {
        // nothing more we can do; the posting is still visible to copy by hand
      } finally {
        document.body.removeChild(textarea)
      }
    }
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(text).then(markCopied).catch(fallback)
    } else {
      fallback()
    }
  }, [])

  const startOver = useCallback(() => {
    setMessages([])
    setDraft('')
    setError(null)
    setCandidateScores([])
    setOpenedPosting(null)
  }, [])

  const handleResumeFiles = useCallback(
    async (fileList) => {
      if (!latestPosting) return
      const files = Array.from(fileList)
      if (files.length === 0) return

      const entries = files.map((file) => ({ id: crypto.randomUUID(), fileName: file.name, status: 'scoring' }))
      setCandidateScores((prev) => [...entries, ...prev])

      await Promise.all(
        files.map(async (file, i) => {
          const entryId = entries[i].id
          try {
            const formData = new FormData()
            formData.append('resume', file)
            formData.append('posting', JSON.stringify(latestPosting))
            const res = await fetch(`${JOBS_URL}/v1/score-resume`, { method: 'POST', body: formData })
            if (!res.ok) throw new Error(`Request failed (${res.status})`)
            const score = await res.json()
            setCandidateScores((prev) =>
              prev.map((e) => (e.id === entryId ? { ...e, status: 'done', score } : e)),
            )
          } catch (err) {
            console.error(err)
            setCandidateScores((prev) =>
              prev.map((e) =>
                e.id === entryId ? { ...e, status: 'error', error: 'Could not score this resume.' } : e,
              ),
            )
          }
        }),
      )
    },
    [latestPosting],
  )

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

      {latestPosting && (
        <div className="score-section open-section">
          {openedPosting?.forMessageId === latestMessageId ? (
            <div className="score-section-row">
              <span>
                Open for applications -- drop resumes into{' '}
                <code className="inbox-path">{openedPosting.inboxDir}</code>, or{' '}
                <Link to="/candidates">review candidates</Link>.
              </span>
            </div>
          ) : (
            <div className="score-section-row">
              <span>
                Open <strong>{latestPosting.title}</strong> for applications
                <span className="optional"> -- watches a folder for incoming resumes and scores them automatically</span>
              </span>
              <button className="copy-button" onClick={handleOpenForApplications} disabled={opening}>
                {opening ? 'Opening…' : 'Open for applications'}
              </button>
            </div>
          )}
        </div>
      )}

      {latestPosting && (
        <div className="score-section">
          <div className="score-section-row">
            <span>
              Score resumes against <strong>{latestPosting.title}</strong>
              <span className="optional"> -- advisory only; HR reviews every candidate</span>
            </span>
            <button className="copy-button" onClick={() => resumeInputRef.current?.click()}>
              Upload resume(s)
            </button>
            <input
              ref={resumeInputRef}
              type="file"
              accept=".pdf,.txt,text/plain,application/pdf"
              multiple
              hidden
              onChange={(e) => {
                handleResumeFiles(e.target.files)
                e.target.value = ''
              }}
            />
          </div>
          {candidateScores.length > 0 && (
            <div className="score-list">
              {candidateScores.map((entry) => (
                <ScoreCard key={entry.id} entry={entry} />
              ))}
            </div>
          )}
        </div>
      )}

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
