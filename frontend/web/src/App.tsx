import { useEffect, useState } from 'react'
import { hasStoredSession, setSignedOutHandler } from './api/client'
import { AuthScreen } from './components/AuthScreen'
import { Dashboard } from './components/Dashboard'

export function App() {
  // A stored refresh token means "probably still signed in". If it turns out to be
  // expired or revoked, the API client calls the signed-out handler below.
  const [signedIn, setSignedIn] = useState(hasStoredSession)

  useEffect(() => {
    setSignedOutHandler(() => setSignedIn(false))
  }, [])

  return signedIn
    ? <Dashboard onSignedOut={() => setSignedIn(false)} />
    : <AuthScreen onSignedIn={() => setSignedIn(true)} />
}
