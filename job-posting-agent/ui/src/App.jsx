import { Link, Route, Routes } from 'react-router-dom'
import PostJobPage from './PostJobPage.jsx'
import CandidatesPage from './CandidatesPage.jsx'
import './App.css'

export default function App() {
  return (
    <div className="page">
      <header className="topbar">
        <Link to="/" className="topbar-brand">
          <img src="/centegy-logo.svg" alt="Centegy Technologies" />
          <span className="topbar-title">HR Job Posting Assistant</span>
        </Link>
        <nav className="topbar-nav">
          <Link to="/candidates">Candidates</Link>
        </nav>
      </header>
      <main className="main">
        <Routes>
          <Route path="/" element={<PostJobPage />} />
          <Route path="/candidates" element={<CandidatesPage />} />
        </Routes>
      </main>
    </div>
  )
}
