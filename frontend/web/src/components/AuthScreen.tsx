import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { login, register } from '../api/endpoints'

export function AuthScreen({ onSignedIn }: { onSignedIn: () => void }) {
  const [mode, setMode] = useState<'signin' | 'register'>('signin')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setFieldErrors({})
    try {
      if (mode === 'register') await register(email, password)
      await login(email, password) // after registering, sign straight in
      onSignedIn()
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.message)
        setFieldErrors(e.fieldErrors)
      } else {
        setError('Could not reach ReturnMate. Is the server running?')
      }
    } finally {
      setBusy(false)
    }
  }

  const registering = mode === 'register'

  return (
    <div className="page auth">
      <h1>ReturnMate</h1>
      <p className="tagline">Never miss a return deadline.</p>

      <form className="card form" onSubmit={handleSubmit}>
        <div className="tabs" role="tablist">
          <button type="button" role="tab" aria-selected={!registering}
                  className={registering ? '' : 'active'} onClick={() => setMode('signin')}>Sign in</button>
          <button type="button" role="tab" aria-selected={registering}
                  className={registering ? 'active' : ''} onClick={() => setMode('register')}>Create account</button>
        </div>

        <label>
          Email
          <input type="email" autoComplete="email" value={email}
                 onChange={e => setEmail(e.target.value)} required />
          {fieldErrors.email && <span className="field-error">{fieldErrors.email}</span>}
        </label>

        <label>
          Password
          <input type="password" autoComplete={registering ? 'new-password' : 'current-password'}
                 value={password} onChange={e => setPassword(e.target.value)} required />
          {registering && (
            <span className="hint">At least 12 characters, with upper and lower case, a number and a symbol.</span>
          )}
          {fieldErrors.password && <span className="field-error">{fieldErrors.password}</span>}
        </label>

        {error && <p className="error" role="alert">{error}</p>}

        <button type="submit" disabled={busy}>
          {busy ? 'Please wait…' : registering ? 'Create account' : 'Sign in'}
        </button>
      </form>
    </div>
  )
}
