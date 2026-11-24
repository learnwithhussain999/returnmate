// One small function per backend endpoint, so components never build URLs themselves.

import { api, clearSession, saveSession, storedRefreshToken } from './client'
import type {
  Category, CreatePurchase, DeadlinePreview, IsoDate, ItemCategory, LoginResponse, Page,
  Purchase, ReturnStatus, Store,
} from './types'

const json = (body: unknown) => JSON.stringify(body)

// ---------------------------------------------------------------- auth
export async function register(email: string, password: string) {
  await api('/api/v1/auth/register', { method: 'POST', body: json({ email, password }) })
}

export async function login(email: string, password: string) {
  saveSession(await api<LoginResponse>('/api/v1/auth/login', {
    method: 'POST', body: json({ email, password }),
  }))
}

export async function logout() {
  const refreshToken = storedRefreshToken()
  try {
    if (refreshToken) {
      await api('/api/v1/auth/logout', { method: 'POST', body: json({ refreshToken }) })
    }
  } finally {
    clearSession() // signed out locally even if the server can't be reached
  }
}

// ---------------------------------------------------------------- purchases
/** Soonest deadline first; purchases with no deadline come last (Postgres sorts NULLs last). */
export function listPurchases() {
  return api<Page<Purchase>>('/api/v1/purchases?size=100&sort=returnDeadline,asc&sort=purchaseDate,desc')
}

export function createPurchase(purchase: CreatePurchase) {
  return api<Purchase>('/api/v1/purchases', { method: 'POST', body: json(purchase) })
}

export function setReturnStatus(id: string, returnStatus: ReturnStatus) {
  return api<Purchase>(`/api/v1/purchases/${id}`, { method: 'PATCH', body: json({ returnStatus }) })
}

export function deletePurchase(id: string) {
  return api<void>(`/api/v1/purchases/${id}`, { method: 'DELETE' })
}

// ---------------------------------------------------------------- stores
export function listStores() {
  return api<Store[]>('/api/v1/stores')
}

export function listCategories() {
  return api<Category[]>('/api/v1/stores/categories')
}

export function previewDeadline(storeCode: string, purchaseDate: IsoDate,
                                category: ItemCategory, deliveryDate?: IsoDate) {
  const params = new URLSearchParams({ purchaseDate, category })
  if (deliveryDate) params.set('deliveryDate', deliveryDate)
  return api<DeadlinePreview>(`/api/v1/stores/${encodeURIComponent(storeCode)}/return-deadline?${params}`)
}
