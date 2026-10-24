import { useCallback, useEffect, useState } from 'react'

const JOBS_URL = import.meta.env.VITE_JOBS_URL || 'http://localhost:8081'

function CandidateCard({ candidate, onApprove, onReject, busy }) {
  const score = candidate.score
  return (
    <div className="score-card candidate-card">
      <div className="score-card-header">
        <h3>{score.candidateName}</h3>
        <span className="score-badge">{score.fitScore}/100</span>
      </div>
      {score.candidateEmail ? (
        <p className="posting-email">{score.candidateEmail}</p>
      ) : (
        <p className="posting-email-missing">No email found on this resume -- cannot approve until one is added.</p>
      )}
      <p>{score.summary}</p>

      {score.missingMustHave.length > 0 && (
        <div className="posting-section">
          <h4>Must-have: missing</h4>
          <ul>
            {score.missingMustHave.map((r, i) => (
              <li key={i}>{r}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="posting-section">
        <h4>For HR</h4>
        <p className="score-recommendation">{score.recommendation}</p>
      </div>

      {candidate.status === 'PENDING' ? (
        <div className="candidate-actions">
          <button className="start-button candidate-approve" onClick={onApprove} disabled={busy || !score.candidateEmail}>
            Approve &amp; invite
          </button>
          <button className="copy-button" onClick={onReject} disabled={busy}>
            Reject
          </button>
        </div>
      ) : (
        <div className={`candidate-status candidate-status-${candidate.status.toLowerCase()}`}>
          {candidate.status === 'APPROVED' ? 'Approved -- screening invite sent' : 'Rejected'}
        </div>
      )}
    </div>
  )
}

function PostingGroup({ posting }) {
  const [candidates, setCandidates] = useState(null)
  const [busyId, setBusyId] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    fetch(`${JOBS_URL}/v1/postings/${posting.id}/candidates`)
      .then((res) => res.json())
      .then(setCandidates)
      .catch((err) => {
        console.error(err)
        setError('Could not load candidates for this posting.')
      })
  }, [posting.id])

  useEffect(() => {
    load()
  }, [load])

  const decide = useCallback(
    async (candidateId, action) => {
      setBusyId(candidateId)
      setError(null)
      try {
        const res = await fetch(`${JOBS_URL}/v1/postings/${posting.id}/candidates/${candidateId}/${action}`, {
          method: 'POST',
        })
        const data = await res.json()
        if (!res.ok) throw new Error(data.error || `Request failed (${res.status})`)
        setCandidates((prev) => prev.map((c) => (c.id === candidateId ? data : c)))
      } catch (err) {
        console.error(err)
        setError(err.message || 'Could not record that decision.')
      } finally {
        setBusyId(null)
      }
    },
    [posting.id],
  )

  const pending = candidates?.filter((c) => c.status === 'PENDING') ?? []
  const decided = candidates?.filter((c) => c.status !== 'PENDING') ?? []

  return (
    <div className="card admin-card posting-group">
      <h1>{posting.jobPosting.title}</h1>
      <p className="subtitle">
        {[posting.jobPosting.employmentType, posting.jobPosting.location].filter(Boolean).join(' · ')}
        {' -- '}
        <code className="inbox-path">{posting.inboxDir}</code>
      </p>

      {error && <div className="error-banner">{error}</div>}

      {candidates === null ? (
        <p className="subtitle">Loading candidates…</p>
      ) : candidates.length === 0 ? (
        <p className="subtitle">No resumes have come in for this posting yet.</p>
      ) : (
        <>
          {pending.length > 0 && (
            <div className="score-list">
              {pending.map((c) => (
                <CandidateCard
                  key={c.id}
                  candidate={c}
                  busy={busyId === c.id}
                  onApprove={() => decide(c.id, 'approve')}
                  onReject={() => decide(c.id, 'reject')}
                />
              ))}
            </div>
          )}
          {decided.length > 0 && (
            <>
              <h3 className="candidates-decided-heading">Decided</h3>
              <div className="score-list">
                {decided.map((c) => (
                  <CandidateCard key={c.id} candidate={c} busy={false} onApprove={() => {}} onReject={() => {}} />
                ))}
              </div>
            </>
          )}
        </>
      )}
    </div>
  )
}

export default function CandidatesPage() {
  const [postings, setPostings] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    fetch(`${JOBS_URL}/v1/postings`)
      .then((res) => res.json())
      .then(setPostings)
      .catch((err) => {
        console.error(err)
        setError('Could not load open postings.')
      })
  }, [])

  return (
    <div className="admin-page candidates-page">
      <div className="card admin-card">
        <h1>Candidates</h1>
        <p className="subtitle">
          Resumes scored against your open postings. Approving sends a screening-interview email;
          rejecting does nothing further. Nothing here happens automatically -- every candidate is
          reviewed by HR.
        </p>
      </div>

      {error && <div className="error-banner">{error}</div>}

      {postings === null ? (
        <div className="card admin-card">
          <p className="subtitle">Loading…</p>
        </div>
      ) : postings.length === 0 ? (
        <div className="card admin-card">
          <p className="subtitle">
            No postings are open for applications yet. Draft one and click "Open for applications"
            to start receiving resumes.
          </p>
        </div>
      ) : (
        postings.map((p) => <PostingGroup key={p.id} posting={p} />)
      )}
    </div>
  )
}
