// Talks to the API and looks after the two tokens.
//
//   access token  (15 min) : kept in MEMORY only. Sent as "Authorization: Bearer ...".
//   refresh token (30 days): kept in localStorage so you stay signed in after closing
//                            the browser. Single use: every refresh returns a new one.
//
// When a request gets 401 because the access token expired, we refresh ONCE and retry.

import type { ApiErrorBody, LoginResponse } from './types'

const REFRESH_KEY = 'returnmate.refreshToken'

let accessToken: string | null = null

/** An error from the API, with the server's message and per-field validation errors. */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

// Storage can be blocked (private mode, strict settings); the app should still work.
function readRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_KEY)
  } catch {
    return null
  }
}

function writeRefreshToken(token: string | null) {
  try {
    if (token) localStorage.setItem(REFRESH_KEY, token)
    else localStorage.removeItem(REFRESH_KEY)
  } catch {
    // ignore: you'll just have to sign in again next time
  }
}

export function saveSession(login: LoginResponse) {
  accessToken = login.accessToken
  writeRefreshToken(login.refreshToken)
}

export function clearSession() {
  accessToken = null
  writeRefreshToken(null)
}

export function hasStoredSession(): boolean {
  return readRefreshToken() !== null
}

export function storedRefreshToken(): string | null {
  return readRefreshToken()
}

/**
 * Get a new access token with the stored refresh token.
 *
 * Refresh tokens are single use, and reusing one makes the server end the session
 * (reuse detection). So two refreshes must never run at once, not even from two tabs:
 *  - inside a tab, concurrent callers share ONE in-flight promise;
 *  - across tabs, the Web Locks API lets only one tab refresh at a time. After waiting
 *    for the lock, a tab re-reads storage: if another tab already refreshed, the token it
 *    finds is the new one, so it never sends a used token.
 */
let refreshInFlight: Promise<boolean> | null = null

export function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    const run = () => doRefresh()
    refreshInFlight = (navigator.locks
      ? navigator.locks.request('returnmate-refresh', run)
      : run()
    ).finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

async function doRefresh(): Promise<boolean> {
  const refreshToken = readRefreshToken()
  if (!refreshToken) return false

  const response = await fetch('/api/v1/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
  if (!response.ok) {
    clearSession() // expired, revoked or reused: sign in again
    return false
  }
  saveSession((await response.json()) as LoginResponse)
  return true
}

/** Called when the session can't be saved: the app shows the sign-in screen. */
let onSignedOut: () => void = () => {}
export function setSignedOutHandler(handler: () => void) {
  onSignedOut = handler
}

/** fetch() for our API: adds the token, refreshes once on 401, turns errors into ApiError. */
export async function api<T>(path: string, init: RequestInit = {}, retried = false): Promise<T> {
  if (!accessToken && !retried && hasStoredSession()) {
    // New tab or page reload: memory is empty but we still have a refresh token.
    await refreshSession()
  }

  const headers = new Headers(init.headers)
  if (init.body) headers.set('Content-Type', 'application/json')
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)

  const response = await fetch(path, { ...init, headers })

  if (response.status === 401 && !retried && !path.startsWith('/api/v1/auth/')) {
    if (await refreshSession()) return api<T>(path, init, true)
    onSignedOut()
  }

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiErrorBody | null
    throw new ApiError(response.status, body?.message ?? `Request failed (${response.status})`,
      body?.validationErrors ?? {})
  }
  return (response.status === 204 ? undefined : await response.json()) as T
}
