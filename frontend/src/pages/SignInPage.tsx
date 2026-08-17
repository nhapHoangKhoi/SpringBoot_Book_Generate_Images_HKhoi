import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { saveUser } from '../auth/session'

/** Email + name, no password. A known email resumes that person's projects (assessment §4.1). */
export function SignInPage() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!name.trim() || !email.includes('@')) {
      setError('Enter your name and a valid email to continue.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      saveUser(await api.signIn(name.trim(), email.trim()))
      navigate('/projects')
    } catch (problem) {
      setError(
        problem instanceof ApiError ? problem.message : 'Could not reach the server. Is it running?',
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="center-page">
      <form className="auth-card" onSubmit={submit} noValidate>
        <div className="logo-row">
          <img src="/logo.png" alt="Gradion" className="logo logo-lg" />
        </div>
        <h3 style={{ textAlign: 'center', fontSize: 20 }}>Book Illustration Studio</h3>
        <p className="meta" style={{ textAlign: 'center', marginBottom: 24 }}>
          Enter your details to start or resume an illustration project.
        </p>
        <div className="field">
          <label htmlFor="name">
            Full name <span className="req">*</span>
          </label>
          <input id="name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Mira Hassan" />
        </div>
        <div className="field">
          <label htmlFor="email">
            Email <span className="req">*</span>
          </label>
          <input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="mira@example.com"
          />
        </div>
        {error && (
          <p className="error-text" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="btn btn-primary btn-block" style={{ marginTop: 24 }} disabled={busy}>
          {busy ? 'Signing in…' : 'Continue →'}
        </button>
        <p className="meta" style={{ textAlign: 'center', marginTop: 14 }}>
          No password. Using an email that already has projects resumes them where you left off.
        </p>
      </form>
    </div>
  )
}
