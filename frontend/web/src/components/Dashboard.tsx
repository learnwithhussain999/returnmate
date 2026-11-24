import { useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import { deletePurchase, listCategories, listPurchases, listStores, logout, setReturnStatus } from '../api/endpoints'
import type { Category, Purchase, ReturnStatus, Store } from '../api/types'
import { formatMoney } from '../lib/dates'
import { AddPurchaseForm } from './AddPurchaseForm'
import { PurchaseRow } from './PurchaseRow'

export function Dashboard({ onSignedOut }: { onSignedOut: () => void }) {
  const [purchases, setPurchases] = useState<Purchase[] | null>(null)
  const [stores, setStores] = useState<Store[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [adding, setAdding] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function load() {
    try {
      const [page, storeList, categoryList] = await Promise.all([listPurchases(), listStores(), listCategories()])
      setPurchases(page.content)
      setStores(storeList)
      setCategories(categoryList)
      setError(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not reach ReturnMate. Is the server running?')
    }
  }

  useEffect(() => { void load() }, [])

  async function changeStatus(id: string, status: ReturnStatus) {
    const updated = await setReturnStatus(id, status)
    setPurchases(list => list?.map(p => (p.id === id ? updated : p)) ?? null)
  }

  async function remove(id: string) {
    await deletePurchase(id)
    setPurchases(list => list?.filter(p => p.id !== id) ?? null)
  }

  async function signOut() {
    await logout()
    onSignedOut()
  }

  const open = purchases?.filter(p => p.returnStatus === 'OPEN') ?? []
  const done = purchases?.filter(p => p.returnStatus !== 'OPEN') ?? []
  const stillReturnable = open.filter(p => !p.expired)
  const endingThisWeek = open.filter(p => p.daysUntilDeadline !== null && p.daysUntilDeadline >= 0 && p.daysUntilDeadline < 7)
  const atStake = stillReturnable.filter(p => p.currency === 'USD').reduce((sum, p) => sum + p.purchaseAmount, 0)

  return (
    <div className="page">
      <header className="topbar">
        <h1>ReturnMate</h1>
        <button className="secondary small" onClick={signOut}>Sign out</button>
      </header>

      {error && <p className="error" role="alert">{error}</p>}

      <section className="stats">
        <Stat label="Ending this week" value={String(endingThisWeek.length)} highlight={endingThisWeek.length > 0} />
        <Stat label="Still returnable" value={String(stillReturnable.length)} />
        <Stat label="Money you can get back" value={formatMoney(atStake, 'USD')} />
      </section>

      {adding ? (
        <AddPurchaseForm
          stores={stores}
          categories={categories}
          onCancel={() => setAdding(false)}
          onSaved={() => { setAdding(false); void load() }} // reload: keeps the server's sort order
        />
      ) : (
        <button className="add" onClick={() => setAdding(true)}>+ Add a purchase</button>
      )}

      {purchases === null && !error && <p className="hint">Loading…</p>}

      {purchases !== null && (
        <>
          <h2 className="section-title">To decide ({open.length})</h2>
          {open.length === 0 ? (
            <p className="empty">Nothing to return. Add something you bought and we'll keep track of the deadline.</p>
          ) : (
            <ul className="purchases">
              {open.map(p => <PurchaseRow key={p.id} purchase={p} onStatus={changeStatus} onDelete={remove} />)}
            </ul>
          )}

          {done.length > 0 && (
            <details className="done-list">
              <summary>Returned or kept ({done.length})</summary>
              <ul className="purchases">
                {done.map(p => <PurchaseRow key={p.id} purchase={p} onStatus={changeStatus} onDelete={remove} />)}
              </ul>
            </details>
          )}
        </>
      )}
    </div>
  )
}

function Stat({ label, value, highlight = false }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div className={`stat ${highlight ? 'highlight' : ''}`}>
      <span className="stat-value">{value}</span>
      <span className="stat-label">{label}</span>
    </div>
  )
}
