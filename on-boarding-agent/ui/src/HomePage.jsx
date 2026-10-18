import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  LiveKitRoom,
  RoomAudioRenderer,
  BarVisualizer,
  TrackToggle,
  DisconnectButton,
  useVoiceAssistant,
  useChat,
  useTranscriptions,
  useLocalParticipant,
} from '@livekit/components-react'
import { Track } from 'livekit-client'

const BRAIN_URL = import.meta.env.VITE_BRAIN_URL || 'http://localhost:8080'

const STATUS_LABEL = {
  connecting: 'Connecting…',
  initializing: 'Connecting…',
  idle: 'Connecting…',
  listening: 'Listening…',
  thinking: 'Thinking…',
  speaking: 'Speaking…',
  disconnected: 'Disconnected',
}

function StartScreen({ onStart, connecting, error, employeeName }) {
  return (
    <div className="card start-card">
      {error && <div className="error-banner">{error}</div>}
      <div className="start-icon">🎙️</div>
      <h1>{employeeName ? `Welcome, ${employeeName}!` : 'HR Onboarding Assistant'}</h1>
      <p className="subtitle">
        {employeeName
          ? "Your HR onboarding session is ready. When you're set, start the conversation."
          : 'Talk or type to your virtual HR assistant for a walkthrough of Centegy Technologies, company policies, benefits, and anything else on your first day.'}
      </p>
      <button className="start-button" onClick={onStart} disabled={connecting}>
        {connecting ? 'Connecting…' : 'Start Conversation'}
      </button>
      <p className="footer-note">Your microphone will be used for this conversation.</p>
    </div>
  )
}

function useConversationFeed() {
  const { chatMessages, send, isSending } = useChat()
  const transcriptions = useTranscriptions()
  const { localParticipant } = useLocalParticipant()

  const feed = useMemo(() => {
    const fromChat = chatMessages.map((m) => ({
      id: m.id,
      timestamp: m.timestamp,
      text: m.message,
      isLocal: m.from?.identity === localParticipant?.identity,
    }))
    const fromTranscripts = transcriptions
      .filter((t) => t.text && t.text.trim().length > 0)
      .map((t) => ({
        id: t.streamInfo.id,
        timestamp: t.streamInfo.timestamp,
        text: t.text,
        isLocal: t.participantInfo.identity === localParticipant?.identity,
      }))
    const merged = new Map()
    for (const item of [...fromTranscripts, ...fromChat]) merged.set(item.id, item)
    return Array.from(merged.values()).sort((a, b) => a.timestamp - b.timestamp)
  }, [chatMessages, transcriptions, localParticipant?.identity])

  return { feed, send, isSending }
}

function Transcript({ feed }) {
  const bottomRef = useRef(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [feed.length])

  if (feed.length === 0) {
    return <div className="transcript-empty">The conversation will appear here.</div>
  }

  return (
    <div className="transcript">
      {feed.map((item) => (
        <div key={item.id} className={`bubble-row ${item.isLocal ? 'local' : 'remote'}`}>
          <div className="bubble">{item.text}</div>
        </div>
      ))}
      <div ref={bottomRef} />
    </div>
  )
}

function ConversationView({ onEnd }) {
  const { state, audioTrack } = useVoiceAssistant()
  const { feed, send, isSending } = useConversationFeed()
  const [draft, setDraft] = useState('')
  const label = STATUS_LABEL[state] || 'Connecting…'

  const handleSend = useCallback(
    (e) => {
      e.preventDefault()
      const text = draft.trim()
      if (!text || isSending) return
      send(text)
      setDraft('')
    },
    [draft, isSending, send],
  )

  return (
    <div className="card conversation-card">
      <div className="conversation-header">
        <div className="status-pill" data-state={state}>
          <span className="status-dot" />
          {label}
        </div>
        <div className="visualizer">
          <BarVisualizer state={state} track={audioTrack} barCount={5} />
        </div>
      </div>

      <Transcript feed={feed} />

      <form className="chat-input-row" onSubmit={handleSend}>
        <input
          type="text"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Type a message instead of speaking…"
          className="chat-input"
        />
        <button type="submit" className="send-button" disabled={!draft.trim() || isSending}>
          Send
        </button>
      </form>

      <div className="controls">
        <TrackToggle className="icon-button" source={Track.Source.Microphone} />
        <DisconnectButton className="end-button" onClick={onEnd}>
          ✕
        </DisconnectButton>
      </div>
      <RoomAudioRenderer />
    </div>
  )
}

export default function HomePage() {
  const [searchParams] = useSearchParams()
  const sessionId = searchParams.get('session')

  const [session, setSession] = useState(null) // { token, serverUrl }
  const [connecting, setConnecting] = useState(false)
  const [error, setError] = useState(null)
  const [employeeName, setEmployeeName] = useState(null)

  useEffect(() => {
    if (!sessionId) return
    fetch(`${BRAIN_URL}/api/admin/sessions/${sessionId}`)
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => data && setEmployeeName(data.name))
      .catch(() => {})
  }, [sessionId])

  const handleStart = useCallback(async () => {
    setConnecting(true)
    setError(null)
    try {
      const url = new URL(`${BRAIN_URL}/api/token`)
      if (sessionId) url.searchParams.set('session', sessionId)
      const res = await fetch(url)
      if (!res.ok) throw new Error(`Token request failed (${res.status})`)
      const data = await res.json()
      setSession({ token: data.token, serverUrl: data.url })
    } catch (err) {
      setError('Could not start the conversation. Please try again.')
      console.error(err)
    } finally {
      setConnecting(false)
    }
  }, [sessionId])

  const handleEnd = useCallback(() => {
    setSession(null)
  }, [])

  if (session) {
    return (
      <LiveKitRoom
        serverUrl={session.serverUrl}
        token={session.token}
        connect
        audio
        onDisconnected={handleEnd}
        onError={(err) => {
          console.error(err)
          setError('Lost connection to the assistant.')
          setSession(null)
        }}
      >
        <ConversationView onEnd={handleEnd} />
      </LiveKitRoom>
    )
  }

  return (
    <StartScreen
      onStart={handleStart}
      connecting={connecting}
      error={error}
      employeeName={employeeName}
    />
  )
}
