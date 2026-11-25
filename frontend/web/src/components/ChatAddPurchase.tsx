import { Fragment, useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { ApiError } from '../api/client'
import { createPurchase, previewDeadline } from '../api/endpoints'
import type { Category, DeadlinePreview, IsoDate, ItemCategory, Purchase, Store } from '../api/types'
import { daysFromToday, describeDaysLeft, formatDate, formatMoney, isoDaysAgo, todayIso } from '../lib/dates'

/*
 * Add a purchase as a conversation: the bot asks one question at a time.
 *
 * How it works: everything is derived from `answers`.
 *  - stepsFor(answers) lists the questions that apply (e.g. "store name" only if you picked
 *    "Another store", "kind of item" only if we're working the date out from a store policy).
 *  - The current question is the first one without an answer.
 *  - The chat history is just: for each answered step, the bot's question + your answer.
 * So "change an earlier answer" = clear that answer (and everything after it), and the
 * conversation rewinds by itself. No scripted message list to keep in sync.
 */

const OTHER_STORE = '__other__'

interface Answers {
  product?: string
  price?: string
  boughtOn?: IsoDate
  store?: string // a store code, or OTHER_STORE
  otherStore?: string
  hasDate?: boolean
  returnDate?: IsoDate
  category?: ItemCategory
  delivery?: IsoDate | null // null = skipped
}
type AnswerKey = keyof Answers
type Step = AnswerKey | 'confirm'

/** Every question in the order it can be asked; used to clear "this answer and everything after it". */
const ORDER: AnswerKey[] = ['product', 'price', 'boughtOn', 'store', 'otherStore', 'hasDate', 'returnDate', 'category', 'delivery']

function stepsFor(a: Answers, knownStore: boolean): Step[] {
  const steps: Step[] = ['product', 'price', 'boughtOn', 'store']
  if (a.store === OTHER_STORE) steps.push('otherStore')
  steps.push('hasDate')
  if (a.hasDate === true) steps.push('returnDate')
  if (a.hasDate === false && knownStore) steps.push('category', 'delivery')
  steps.push('confirm')
  return steps
}

interface Props {
  stores: Store[]
  categories: Category[]
  onSaved: (purchase: Purchase) => void // called after each save; the chat stays open
  onUseForm: () => void
}

export function ChatAddPurchase({ stores, categories, onSaved, onUseForm }: Props) {
  const [answers, setAnswers] = useState<Answers>({})
  const [preview, setPreview] = useState<DeadlinePreview | null | 'loading' | 'failed'>(null)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saved, setSaved] = useState<Purchase | null>(null)
  const logRef = useRef<HTMLDivElement>(null)

  const store = stores.find(s => s.code === answers.store) ?? null
  const steps = stepsFor(answers, store !== null)
  const current = steps.find(s => s === 'confirm' || answers[s] === undefined) ?? 'confirm'
  const answered = steps.slice(0, steps.indexOf(current))
  const usePolicy = store !== null && answers.hasDate === false

  // Keep the newest message in view.
  useEffect(() => {
    const log = logRef.current
    if (log) log.scrollTop = log.scrollHeight
  }, [current, preview, saveError, saved])

  // Reaching the summary with no date of their own: ask the server what the store policy gives.
  useEffect(() => {
    if (current !== 'confirm' || !usePolicy || !store || !answers.boughtOn) {
      setPreview(null)
      return
    }
    let cancelled = false
    setPreview('loading')
    previewDeadline(store.code, answers.boughtOn, answers.category ?? 'GENERAL', answers.delivery ?? undefined)
      .then(result => { if (!cancelled) setPreview(result) })
      .catch(() => { if (!cancelled) setPreview('failed') })
    return () => { cancelled = true }
  }, [current, usePolicy, store, answers.boughtOn, answers.category, answers.delivery])

  function answer<K extends AnswerKey>(key: K, value: Answers[K]) {
    setSaveError(null)
    setAnswers(a => ({ ...a, [key]: value }))
  }

  /** Rewind: clear this answer and every answer after it. */
  function changeFrom(key: AnswerKey) {
    setSaveError(null)
    setAnswers(a => {
      const next = { ...a }
      for (const k of ORDER.slice(ORDER.indexOf(key))) delete next[k]
      return next
    })
  }

  function startOver() {
    setAnswers({})
    setSaved(null)
    setSaveError(null)
  }

  async function save() {
    const merchantName = store ? store.name : (answers.otherStore ?? '').trim()
    setSaving(true)
    setSaveError(null)
    try {
      const purchase = await createPurchase({
        merchantName,
        productName: (answers.product ?? '').trim(),
        purchaseDate: answers.boughtOn!,
        purchaseAmount: Number(answers.price),
        currency: 'USD',
        returnDeadline: answers.hasDate ? answers.returnDate : undefined,
        deliveryDate: answers.delivery ?? undefined,
        storeCode: store?.code,
        itemCategory: store ? (answers.category ?? 'GENERAL') : undefined,
      })
      setSaved(purchase)
      onSaved(purchase)
    } catch (e) {
      if (e instanceof ApiError) {
        const details = Object.values(e.fieldErrors)
        setSaveError(details.length ? `${e.message}: ${details.join(', ')}` : e.message)
      } else {
        setSaveError('I could not reach ReturnMate. Is the server running?')
      }
    } finally {
      setSaving(false)
    }
  }

  // ------------------------------------------------------------------ what the bot says
  function question(step: Step): ReactNode {
    switch (step) {
      case 'product': return "Hi! Tell me what you bought and I'll keep track of the return deadline. What's the item?"
      case 'price': return `How much did you pay for “${answers.product}”?`
      case 'boughtOn': return 'When did you buy it?'
      case 'store': return 'Where did you buy it?'
      case 'otherStore': return "What's the store called?"
      case 'hasDate': return store
        ? `Do you know the last day you can return it? If not, I'll work it out from ${store.name}'s return policy.`
        : `Do you know the last day you can return it? I don't know ${answers.otherStore}'s return policy, so I can only remind you if you tell me the date.`
      case 'returnDate': return "What's the last day to return it? It's often printed on the receipt."
      case 'category': return 'What kind of item is it? Some stores give items like phones or Apple products a different return window.'
      case 'delivery': return 'Was it delivered? Some stores count the return window from the delivery day. Skip this if you picked it up in the store.'
      case 'confirm': return summary()
    }
  }

  // ------------------------------------------------------------------ your answers, as shown in the chat
  function answerText(step: AnswerKey): string {
    switch (step) {
      case 'product': return answers.product ?? ''
      case 'price': return formatMoney(Number(answers.price), 'USD')
      case 'boughtOn': return dateWithRelative(answers.boughtOn!)
      case 'store': return store?.name ?? 'Another store'
      case 'otherStore': return answers.otherStore ?? ''
      case 'hasDate': return answers.hasDate ? 'Yes, I know the date' : store ? 'No, work it out for me' : "No, I don't know it"
      case 'returnDate': return formatDate(answers.returnDate!)
      case 'category': return categories.find(c => c.code === answers.category)?.label ?? 'General'
      case 'delivery': return answers.delivery ? dateWithRelative(answers.delivery) : 'Skip'
    }
  }

  function summary(): ReactNode {
    return (
      <>
        <p>Here's what I've got:</p>
        <dl className="chat-summary">
          <dt>Item</dt><dd>{answers.product}</dd>
          <dt>Price</dt><dd>{formatMoney(Number(answers.price), 'USD')}</dd>
          <dt>Bought</dt><dd>{formatDate(answers.boughtOn!)}</dd>
          <dt>Store</dt><dd>{store?.name ?? answers.otherStore}</dd>
          <dt>Return by</dt><dd>{deadline()}</dd>
        </dl>
        <p>Shall I save it?</p>
      </>
    )
  }

  function deadline(): ReactNode {
    if (answers.hasDate && answers.returnDate) {
      return <><strong>{formatDate(answers.returnDate)}</strong> · {describeDaysLeft(daysFromToday(answers.returnDate))}</>
    }
    if (!store) return <span className="warn">No date, so I can't remind you. You can add one later.</span>
    if (preview === 'loading' || preview === null) return <span className="muted">Working it out…</span>
    if (preview === 'failed') return <span className="muted">I couldn't check right now, but it's worked out when you save.</span>
    if (!preview.returnDeadline) return <span className="muted">{preview.explanation}</span>
    return (
      <>
        <strong>{formatDate(preview.returnDeadline)}</strong> · {describeDaysLeft(daysFromToday(preview.returnDeadline))}
        <span className="muted chat-explain">{preview.explanation}</span>
      </>
    )
  }

  // ------------------------------------------------------------------ layout
  const locked = saving || saved !== null

  return (
    <section className="card chat" aria-label="Add a purchase">
      <header className="chat-header">
        <h2>Add a purchase</h2>
        <button type="button" className="small link" onClick={onUseForm}>Use the form instead</button>
      </header>

      <div className="chat-log" ref={logRef} role="log" aria-live="polite">
        {answered.map(step => step !== 'confirm' && (
          <Fragment key={step}>
            <Bubble from="bot">{question(step)}</Bubble>
            <Bubble from="you" onChange={locked ? undefined : () => changeFrom(step)}>{answerText(step)}</Bubble>
          </Fragment>
        ))}

        {!saved && <Bubble from="bot">{question(current)}</Bubble>}
        {saveError && <Bubble from="bot" tone="error">Sorry, that didn't save. {saveError}</Bubble>}

        {saved && (
          <>
            <Bubble from="you">Save it</Bubble>
            <Bubble from="bot">
              Saved! “{saved.productName}”{' '}
              {saved.returnDeadline
                ? <>can be returned until <strong>{formatDate(saved.returnDeadline)}</strong> ({describeDaysLeft(saved.daysUntilDeadline ?? 0).toLowerCase()}).</>
                : 'is on your list without a return date.'}{' '}
              Anything else you bought?
            </Bubble>
          </>
        )}
      </div>

      <div className="chat-composer">
        {saved ? (
          <div className="chips">
            <button type="button" onClick={startOver} autoFocus>Add another</button>
          </div>
        ) : (
          <Composer key={current} step={current} answers={answers} stores={stores} categories={categories}
                    store={store} saving={saving} onAnswer={answer} onSave={save}
                    onChangeLast={() => changeFrom(answered[answered.length - 1] as AnswerKey)}
                    onStartOver={startOver} />
        )}
      </div>
    </section>
  )
}

function dateWithRelative(date: IsoDate): string {
  const days = daysFromToday(date)
  const label = formatDate(date)
  return days === 0 ? `Today, ${label}` : days === -1 ? `Yesterday, ${label}` : label
}

function Bubble({ from, tone, onChange, children }: {
  from: 'bot' | 'you'
  tone?: 'error'
  onChange?: () => void
  children: ReactNode
}) {
  return (
    <div className={`bubble ${from} ${tone ?? ''}`}>
      <div className="bubble-text">{children}</div>
      {onChange && <button type="button" className="bubble-change" onClick={onChange}>Change</button>}
    </div>
  )
}

// ==================================================================== the input box for each question

interface ComposerProps {
  step: Step
  answers: Answers
  stores: Store[]
  categories: Category[]
  store: Store | null
  saving: boolean
  onAnswer: <K extends AnswerKey>(key: K, value: Answers[K]) => void
  onSave: () => void
  onChangeLast: () => void
  onStartOver: () => void
}

/**
 * Remounted for every question (key={current}), so each one starts with an empty input
 * and its own error message. Validation here is for quick feedback; the server still checks everything.
 */
function Composer({ step, answers, stores, categories, saving, onAnswer, onSave, onChangeLast, onStartOver }: ComposerProps) {
  const today = todayIso()
  const boughtOn = answers.boughtOn ?? today

  switch (step) {
    case 'product':
      return <InputComposer placeholder="e.g. Sony WH-1000XM5 headphones" maxLength={255}
                            validate={v => (v.trim() ? null : 'Type what you bought')}
                            onSubmit={v => onAnswer('product', v.trim())} />
    case 'price':
      return <InputComposer type="number" inputMode="decimal" placeholder="Price in USD, e.g. 348.00" prefix="$"
                            validate={v => (/^\d+(\.\d{1,2})?$/.test(v) && Number(v) > 0 ? null : 'Enter an amount like 348 or 348.99')}
                            onSubmit={v => onAnswer('price', v)} />
    case 'boughtOn':
      return (
        <>
          <div className="chips">
            <button type="button" className="secondary" onClick={() => onAnswer('boughtOn', today)}>Today</button>
            <button type="button" className="secondary" onClick={() => onAnswer('boughtOn', isoDaysAgo(1))}>Yesterday</button>
          </div>
          <InputComposer type="date" max={today} ariaLabel="Purchase date"
                         validate={v => (!v ? 'Pick a date' : v > today ? "That's in the future" : null)}
                         onSubmit={v => onAnswer('boughtOn', v)} />
        </>
      )
    case 'store':
      return (
        <SelectComposer ariaLabel="Store" placeholder="Choose a store…"
                        options={[...stores.map(s => ({ value: s.code, label: s.name })), { value: OTHER_STORE, label: 'Another store…' }]}
                        onSubmit={v => onAnswer('store', v)} />
      )
    case 'otherStore':
      return <InputComposer placeholder="e.g. Corner Electronics" maxLength={150}
                            validate={v => (v.trim() ? null : 'Type the store name')}
                            onSubmit={v => onAnswer('otherStore', v.trim())} />
    case 'hasDate':
      return (
        <div className="chips">
          <button type="button" className="secondary" onClick={() => onAnswer('hasDate', true)}>Yes, I know the date</button>
          <button type="button" className="secondary" onClick={() => onAnswer('hasDate', false)}>
            {answers.store === OTHER_STORE ? "No, I don't know it" : 'No, work it out for me'}
          </button>
        </div>
      )
    case 'returnDate':
      return <InputComposer type="date" min={boughtOn} ariaLabel="Last day to return"
                            validate={v => (!v ? 'Pick a date' : v < boughtOn ? 'It has to be on or after the purchase date' : null)}
                            onSubmit={v => onAnswer('returnDate', v)} />
    case 'category':
      return (
        <SelectComposer ariaLabel="Kind of item" initial="GENERAL"
                        options={categories.map(c => ({ value: c.code, label: c.label }))}
                        onSubmit={v => onAnswer('category', v as ItemCategory)} />
      )
    case 'delivery':
      return (
        <>
          <div className="chips">
            <button type="button" className="secondary" onClick={() => onAnswer('delivery', null)}>Skip</button>
          </div>
          <InputComposer type="date" min={boughtOn} max={today} ariaLabel="Delivery date"
                         validate={v => (!v ? 'Pick a date, or press Skip' : v < boughtOn ? 'It has to be on or after the purchase date' : null)}
                         onSubmit={v => onAnswer('delivery', v)} />
        </>
      )
    case 'confirm':
      return (
        <div className="chips">
          <button type="button" onClick={onSave} disabled={saving} autoFocus>{saving ? 'Saving…' : 'Save it'}</button>
          <button type="button" className="secondary" onClick={onChangeLast} disabled={saving}>Change last answer</button>
          <button type="button" className="link" onClick={onStartOver} disabled={saving}>Start over</button>
        </div>
      )
  }
}

function InputComposer({ type = 'text', inputMode, placeholder, prefix, min, max, maxLength, ariaLabel, validate, onSubmit }: {
  type?: 'text' | 'number' | 'date'
  inputMode?: 'decimal'
  placeholder?: string
  prefix?: string
  min?: string
  max?: string
  maxLength?: number
  ariaLabel?: string
  validate: (value: string) => string | null
  onSubmit: (value: string) => void
}) {
  const [value, setValue] = useState('')
  const [error, setError] = useState<string | null>(null)

  function submit(event: FormEvent) {
    event.preventDefault()
    const problem = validate(value)
    if (problem) setError(problem)
    else onSubmit(value)
  }

  return (
    <form className="chat-input" onSubmit={submit} noValidate>
      <div className="chat-input-row">
        {prefix && <span className="chat-prefix" aria-hidden>{prefix}</span>}
        <input type={type} inputMode={inputMode} placeholder={placeholder} min={min} max={max} maxLength={maxLength}
               step={type === 'number' ? '0.01' : undefined} aria-label={ariaLabel ?? placeholder}
               aria-invalid={error !== null} value={value} autoFocus
               onChange={e => { setValue(e.target.value); setError(null) }} />
        <button type="submit">Send</button>
      </div>
      {error && <span className="field-error">{error}</span>}
    </form>
  )
}

function SelectComposer({ options, placeholder, initial = '', ariaLabel, onSubmit }: {
  options: { value: string; label: string }[]
  placeholder?: string
  initial?: string
  ariaLabel: string
  onSubmit: (value: string) => void
}) {
  const [value, setValue] = useState(initial)
  return (
    <form className="chat-input" onSubmit={e => { e.preventDefault(); if (value) onSubmit(value) }}>
      <div className="chat-input-row">
        <select value={value} onChange={e => setValue(e.target.value)} aria-label={ariaLabel} autoFocus>
          {placeholder && <option value="" disabled>{placeholder}</option>}
          {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        <button type="submit" disabled={!value}>Send</button>
      </div>
    </form>
  )
}
