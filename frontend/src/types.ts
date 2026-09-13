export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface Customer {
  id: number
  name: string
  phone: string | null
  wechat: string | null
  tags: string | null
  notes: string | null
  createdAt: string
  updatedAt: string
  relationship: RelationshipProfile
}

export interface RelationshipProfile {
  entityType: string
  industry: string
  organization: string | null
  jobTitle: string | null
  email: string | null
  relationshipType: string
  stage: string
  needs: string | null
}

export type CustomerDraft = Pick<Customer, 'name' | 'phone' | 'wechat' | 'tags' | 'notes' | 'relationship'>

export interface ContactRecord {
  id: number
  customerId: number
  contactAt: string
  channel: string
  summary: string
  nextContactAt: string | null
  createdAt: string
}

export interface Consumption {
  id: number
  customerId: number
  occurredAt: string
  amount: number
  itemName: string
  note: string | null
  createdAt: string
}

export interface Overview {
  customerCount: number
  dueFollowUps: number
  totalConsumption: number
  plannedFollowUps: number
}

export interface Reminder {
  customerId: number
  customerName: string
  dueAt: string
  lastSummary: string
}

export interface OperationLog {
  id: number
  action: string
  actionLabel: string
  targetLabel: string
  targetType: string
  targetId: number | null
  detail: string | null
  createdAt: string
}
