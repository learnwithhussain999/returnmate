// The shapes the Spring Boot API sends and accepts. Each one mirrors a Java record
// (named in the comment), so if the backend renames a field, TypeScript shows every
// place in the UI that needs changing.

/** "2026-11-24": the API sends dates (LocalDate) as ISO strings. */
export type IsoDate = string

export type ReturnStatus = 'OPEN' | 'RETURNED' | 'KEPT'

export type DeadlineSource =
  | 'RECEIPT'
  | 'MERCHANT_POLICY'
  | 'HOLIDAY_EXTENSION'
  | 'DEFAULT_WINDOW'
  | 'USER_ENTERED'

export type ItemCategory =
  | 'GENERAL'
  | 'ELECTRONICS'
  | 'PHONE'
  | 'APPLE'
  | 'MAJOR_APPLIANCE'
  | 'AIR_CONDITIONER'
  | 'FURNITURE'
  | 'MATTRESS'
  | 'JEWELRY_WATCHES'
  | 'BEAUTY'
  | 'PLANTS'

/** LoginResponse.java */
export interface LoginResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  refreshToken: string
}

/** PurchaseResponse.java */
export interface Purchase {
  id: string
  merchantName: string
  storeCode: string | null
  productName: string
  itemCategory: ItemCategory
  purchaseDate: IsoDate
  deliveryDate: IsoDate | null
  purchaseAmount: number
  currency: string
  orderNumber: string | null
  returnDeadline: IsoDate | null
  deadlineSource: DeadlineSource | null
  returnStatus: ReturnStatus
  daysUntilDeadline: number | null
  expired: boolean
  source: 'MANUAL' | 'PHOTO' | 'EMAIL'
  createdAt: string
  updatedAt: string
}

/** CreatePurchaseRequest.java (fields left out = null on the server) */
export interface CreatePurchase {
  merchantName: string
  productName: string
  purchaseDate: IsoDate
  purchaseAmount: number
  currency: string
  returnDeadline?: IsoDate
  deliveryDate?: IsoDate
  storeCode?: string
  itemCategory?: ItemCategory
}

/** Spring Data's Page<T>, only the parts we use. */
export interface Page<T> {
  content: T[]
  totalElements: number
}

/** StoreResponse.java */
export interface Store {
  code: string
  name: string
  defaultReturnDays: number | null
  categoryReturnDays: Partial<Record<ItemCategory, number>>
  policyUrl: string
  notes: string | null
}

/** StoreResponse.Category */
export interface Category {
  code: ItemCategory
  label: string
}

/** StoreResponse.DeadlinePreview */
export interface DeadlinePreview {
  storeCode: string
  storeName: string
  returnDeadline: IsoDate | null
  deadlineSource: DeadlineSource | null
  explanation: string
  policyUrl: string
}

/** ErrorResponse.java: every error the API returns has this shape. */
export interface ApiErrorBody {
  status: number
  message: string
  validationErrors?: Record<string, string>
}
