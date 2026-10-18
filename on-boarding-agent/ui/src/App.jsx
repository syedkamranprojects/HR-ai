import { Routes, Route, Link } from 'react-router-dom'
import HomePage from './HomePage.jsx'
import AdminPage from './AdminPage.jsx'
import './App.css'

export default function App() {
  return (
    <div className="page">
      <header className="topbar">
        <Link to="/" className="topbar-brand">
          <img src="/centegy-logo.svg" alt="Centegy Technologies" />
          <span className="topbar-title">HR Onboarding Assistant</span>
        </Link>
        <nav className="topbar-nav">
          <Link to="/admin">Admin</Link>
        </nav>
      </header>
      <main className="main">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/admin" element={<AdminPage />} />
        </Routes>
      </main>
    </div>
  )
}
