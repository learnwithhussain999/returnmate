import type { Purchase, ReturnStatus } from '../api/types'
import { describeDaysLeft, formatDate, formatMoney, urgency } from '../lib/dates'

interface Props {
  purchase: Purchase
  onStatus: (id: string, status: ReturnStatus) => void
  onDelete: (id: string) => void
}

const SOURCE_LABEL: Record<string, string> = {
  HOLIDAY_EXTENSION: 'holiday extension',
  MERCHANT_POLICY: 'store policy',
  USER_ENTERED: 'your date',
  RECEIPT: 'from receipt',
}

export function PurchaseRow({ purchase: p, onStatus, onDelete }: Props) {
  const open = p.returnStatus === 'OPEN'
  const level = open ? urgency(p.daysUntilDeadline) : 'none'

  return (
    <li className={`purchase ${level} ${open ? '' : 'done'}`}>
      <div className="purchase-main">
        <span className="product">{p.productName}</span>
        <span className="meta">
          {p.merchantName} · {formatMoney(p.purchaseAmount, p.currency)} · bought {formatDate(p.purchaseDate)}
        </span>
      </div>

      <div className="purchase-deadline">
        {!open && <span className="status">{p.returnStatus === 'RETURNED' ? 'Returned' : 'Kept'}</span>}
        {open && p.returnDeadline && p.daysUntilDeadline !== null && (
          <>
            <span className="days">{describeDaysLeft(p.daysUntilDeadline)}</span>
            <span className="meta">
              Return by {formatDate(p.returnDeadline)}
              {p.deadlineSource && SOURCE_LABEL[p.deadlineSource] ? ` · ${SOURCE_LABEL[p.deadlineSource]}` : ''}
            </span>
          </>
        )}
        {open && !p.returnDeadline && (
          <span className="meta">{p.storeCode ? 'No fixed return limit' : 'No return date'}</span>
        )}
      </div>

      <div className="purchase-actions">
        {open ? (
          <>
            <button className="small" onClick={() => onStatus(p.id, 'RETURNED')}>Returned</button>
            <button className="small secondary" onClick={() => onStatus(p.id, 'KEPT')}>Keeping it</button>
          </>
        ) : (
          <button className="small secondary" onClick={() => onStatus(p.id, 'OPEN')}>Undo</button>
        )}
        <button className="small link" aria-label={`Delete ${p.productName}`}
                onClick={() => { if (window.confirm(`Delete "${p.productName}"?`)) onDelete(p.id) }}>
          Delete
        </button>
      </div>
    </li>
  )
}
