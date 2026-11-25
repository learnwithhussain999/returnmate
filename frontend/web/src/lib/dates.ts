import type { IsoDate } from '../api/types'

/** Today as "YYYY-MM-DD" in the user's own timezone (toISOString() would use UTC). */
export function todayIso(): IsoDate {
  return toIso(new Date())
}

/** The date n days before today, e.g. isoDaysAgo(1) = yesterday. */
export function isoDaysAgo(n: number): IsoDate {
  const date = new Date()
  date.setDate(date.getDate() - n) // setDate handles month and year boundaries
  return toIso(date)
}

function toIso(date: Date): IsoDate {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/**
 * Whole days from today to the date: 0 = today, negative = passed.
 * Both dates are treated as UTC midnight so daylight-saving changes can't make it off by one.
 */
export function daysFromToday(date: IsoDate): number {
  const toUtc = (iso: string) => {
    const [y, m, d] = iso.split('-').map(Number)
    return Date.UTC(y, m - 1, d)
  }
  return Math.round((toUtc(date) - toUtc(todayIso())) / 86_400_000)
}

/** "Jan 15, 2027" */
export function formatDate(date: IsoDate): string {
  const [y, m, d] = date.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

/** "52 days left", "Last day today", "Ended 3 days ago" */
export function describeDaysLeft(days: number): string {
  if (days > 1) return `${days} days left`
  if (days === 1) return '1 day left'
  if (days === 0) return 'Last day today'
  return days === -1 ? 'Ended yesterday' : `Ended ${-days} days ago`
}

/** Design doc colours: red under 3 days, amber under 7. */
export function urgency(days: number | null): 'urgent' | 'soon' | 'ok' | 'none' | 'past' {
  if (days === null) return 'none'
  if (days < 0) return 'past'
  if (days < 3) return 'urgent'
  if (days < 7) return 'soon'
  return 'ok'
}

export function formatMoney(amount: number, currency: string): string {
  try {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount)
  } catch {
    return `${amount.toFixed(2)} ${currency}`
  }
}
