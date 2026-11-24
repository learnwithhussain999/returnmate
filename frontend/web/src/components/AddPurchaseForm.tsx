import { useEffect, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { createPurchase, previewDeadline } from '../api/endpoints'
import type { Category, DeadlinePreview, ItemCategory, Purchase, Store } from '../api/types'
import { daysFromToday, describeDaysLeft, formatDate, todayIso } from '../lib/dates'

const OTHER_STORE = '__other__'

interface Props {
  stores: Store[]
  categories: Category[]
  onSaved: (purchase: Purchase) => void
  onCancel: () => void
}

/**
 * Add a purchase. The return date is optional:
 *  - know it?           type it in, and it's used as-is;
 *  - don't know it?     pick the store and kind of item, and the server works it out
 *                       from the store's policy (shown live below the form before saving);
 *  - unknown store and no date? It's saved without a deadline. We never guess one.
 */
export function AddPurchaseForm({ stores, categories, onSaved, onCancel }: Props) {
  const [productName, setProductName] = useState('')
  const [price, setPrice] = useState('')
  const [purchaseDate, setPurchaseDate] = useState(todayIso())
  const [storeCode, setStoreCode] = useState('')
  const [otherStoreName, setOtherStoreName] = useState('')
  const [returnDeadline, setReturnDeadline] = useState('')
  const [category, setCategory] = useState<ItemCategory>('GENERAL')
  const [deliveryDate, setDeliveryDate] = useState('')

  const [preview, setPreview] = useState<DeadlinePreview | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const store = stores.find(s => s.code === storeCode) ?? null
  const isOtherStore = storeCode === OTHER_STORE
  const usePolicy = store !== null && returnDeadline === ''

  // Live preview of the store-policy deadline. Re-runs whenever an input changes; the
  // "cancelled" flag throws away an older answer that arrives after a newer request.
  useEffect(() => {
    if (!usePolicy || !store || !purchaseDate) {
      setPreview(null)
      return
    }
    let cancelled = false
    const timer = setTimeout(() => {
      previewDeadline(store.code, purchaseDate, category, deliveryDate || undefined)
        .then(result => { if (!cancelled) setPreview(result) })
        .catch(() => { if (!cancelled) setPreview(null) })
    }, 250) // wait for the user to stop typing
    return () => { cancelled = true; clearTimeout(timer) }
  }, [usePolicy, store, purchaseDate, category, deliveryDate])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    const merchantName = store ? store.name : otherStoreName.trim()
    if (!merchantName) {
      setFieldErrors({ merchantName: 'Choose the store, or type its name' })
      return
    }

    setSaving(true)
    try {
      const saved = await createPurchase({
        merchantName,
        productName: productName.trim(),
        purchaseDate,
        purchaseAmount: Number(price),
        currency: 'USD',
        returnDeadline: returnDeadline || undefined,
        deliveryDate: deliveryDate || undefined,
        storeCode: store?.code,
        itemCategory: store ? category : undefined,
      })
      onSaved(saved)
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.message)
        setFieldErrors(e.fieldErrors)
      } else {
        setError('Could not reach ReturnMate. Is the server running?')
      }
    } finally {
      setSaving(false)
    }
  }

  const typedDaysLeft = returnDeadline ? daysFromToday(returnDeadline) : null

  return (
    <form className="card form" onSubmit={handleSubmit} noValidate>
      <h2>Add a purchase</h2>

      <label>
        What did you buy?
        <input value={productName} onChange={e => setProductName(e.target.value)}
               placeholder="Sony WH-1000XM5 headphones" required maxLength={255} autoFocus />
        <FieldError message={fieldErrors.productName} />
      </label>

      <div className="row">
        <label>
          Price (USD)
          <input type="number" inputMode="decimal" min="0.01" step="0.01" value={price}
                 onChange={e => setPrice(e.target.value)} placeholder="348.00" required />
          <FieldError message={fieldErrors.purchaseAmount} />
        </label>
        <label>
          Bought on
          <input type="date" value={purchaseDate} max={todayIso()}
                 onChange={e => setPurchaseDate(e.target.value)} required />
          <FieldError message={fieldErrors.purchaseDate} />
        </label>
      </div>

      <label>
        Where did you buy it?
        <select value={storeCode} onChange={e => setStoreCode(e.target.value)} required>
          <option value="" disabled>Choose a store…</option>
          {stores.map(s => <option key={s.code} value={s.code}>{s.name}</option>)}
          <option value={OTHER_STORE}>Another store…</option>
        </select>
        <FieldError message={fieldErrors.merchantName} />
      </label>

      {isOtherStore && (
        <label>
          Store name
          <input value={otherStoreName} onChange={e => setOtherStoreName(e.target.value)}
                 placeholder="Corner Electronics" maxLength={150} />
        </label>
      )}

      <label>
        Last day to return it <span className="optional">(optional)</span>
        <input type="date" value={returnDeadline} min={purchaseDate}
               onChange={e => setReturnDeadline(e.target.value)} />
        <span className="hint">
          {store
            ? `Leave empty and we'll work it out from ${store.name}'s return policy.`
            : 'Check your receipt or the store\'s website.'}
        </span>
        <FieldError message={fieldErrors.deadlineOnOrAfterPurchase} />
      </label>

      {usePolicy && (
        <div className="row">
          <label>
            Kind of item
            <select value={category} onChange={e => setCategory(e.target.value as ItemCategory)}>
              {categories.map(c => <option key={c.code} value={c.code}>{c.label}</option>)}
            </select>
          </label>
          <label>
            Delivered on <span className="optional">(optional)</span>
            <input type="date" value={deliveryDate} min={purchaseDate}
                   onChange={e => setDeliveryDate(e.target.value)} />
            <FieldError message={fieldErrors.deliveryOnOrAfterPurchase} />
          </label>
        </div>
      )}

      {/* What the user will get, before they save. */}
      <div className="deadline-preview" aria-live="polite">
        {typedDaysLeft !== null && returnDeadline && (
          <p><strong>Return by {formatDate(returnDeadline)}</strong> · {describeDaysLeft(typedDaysLeft)}</p>
        )}
        {usePolicy && preview?.returnDeadline && (
          <>
            <p>
              <strong>Return by {formatDate(preview.returnDeadline)}</strong> ·{' '}
              {describeDaysLeft(daysFromToday(preview.returnDeadline))}
            </p>
            <p className="hint">{preview.explanation}</p>
          </>
        )}
        {usePolicy && preview && !preview.returnDeadline && (
          <p className="hint">{preview.explanation}</p>
        )}
        {isOtherStore && !returnDeadline && (
          <p className="hint warn">
            We don't know this store's return policy, so we can't remind you unless you add the date.
          </p>
        )}
      </div>

      {error && <p className="error" role="alert">{error}</p>}

      <div className="actions">
        <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
        <button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save purchase'}</button>
      </div>
    </form>
  )
}

function FieldError({ message }: { message?: string }) {
  return message ? <span className="field-error">{message}</span> : null
}
