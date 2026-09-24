/**
 * DTOs of the Spring Boot REST API (see /v3/api-docs). Kept hand-written to express nullability precisely; the
 * backend is the source of truth for every business decision these types carry (policy, status, thresholds).
 */

export type AssetType =
  | 'PRODUCT_IMAGE'
  | 'CAMPAIGN_IMAGE'
  | 'MODEL_IMAGE'
  | 'VIDEO'
  | 'DOCUMENT'
  | 'DIGITAL_TWIN'
  | 'OTHER'

export const ASSET_TYPES: AssetType[] = [
  'PRODUCT_IMAGE',
  'CAMPAIGN_IMAGE',
  'MODEL_IMAGE',
  'VIDEO',
  'DOCUMENT',
  'DIGITAL_TWIN',
  'OTHER',
]

export type AssetStatus = 'DRAFT' | 'IN_REVIEW' | 'READY' | 'ARCHIVED'
export type MetadataSource = 'MANUAL' | 'EMBEDDED' | 'AI_GENERATED' | 'PRODUCT_SYSTEM' | 'DAM' | 'DERIVED'
export type MetadataStatus = 'PROPOSED' | 'APPROVED' | 'REJECTED' | 'VALIDATED'
export type ApprovalRoute = 'AUTO_APPROVE' | 'HUMAN_REVIEW' | 'LOW_CONFIDENCE' | 'REJECTED_BY_POLICY' | 'NO_CHANGE'
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'
export type MetadataDataType = 'STRING' | 'TEXT' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'DATE' | 'URL' | 'TAXONOMY_PATH'
export type AiStatus = 'NOT_ENRICHED' | 'QUEUED' | 'RUNNING' | 'REVIEW_PENDING' | 'FAILED' | 'ENRICHED'
export type RightsStatus = 'VALID' | 'EXPIRING' | 'EXPIRED' | 'MISSING'
export type RunStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'
export type StageStatus = 'WAITING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED'
export type StepKind = 'AI_AGENT' | 'DETERMINISTIC_STEP'
export type CapabilityState = 'AVAILABLE' | 'UNAVAILABLE' | 'DISABLED'
export type IssueSeverity = 'ERROR' | 'WARNING'
export type AuditAction =
  | 'VALUE_IMPORTED'
  | 'VALUE_SET_MANUALLY'
  | 'VALUE_REMOVED'
  | 'VALUE_AUTO_APPLIED'
  | 'PROPOSAL_APPROVED'
  | 'PROPOSAL_REJECTED'
  | 'PROPOSAL_SUPERSEDED'

/** String, number, boolean, ISO date string or a list of scalars for repeatable fields. */
export type MetadataScalar = string | number | boolean
export type MetadataValue = MetadataScalar | MetadataScalar[] | null

export interface Asset {
  id: string
  externalAssetId: string
  filename: string
  mimeType: string
  assetUrl: string
  type: AssetType
  status: AssetStatus
  metadataRevision: number
  createdAt: string
  updatedAt: string
}

export interface MetadataSummary {
  valid: boolean
  completeness: number
  requiredCompleteness: number
  qualityScore: number
  missingRequiredFields: string[]
  errorCount: number
  warningCount: number
  aiValueCount: number
  pendingReviewCount: number
  lowConfidenceCount: number
  usageRightsEnd: string | null
  refreshedAt: string
}

export interface LastEnrichment {
  workflowId: string
  status: RunStatus
  createdAt: string
  finishedAt: string | null
}

export interface CatalogItem {
  id: string
  externalAssetId: string
  filename: string
  mimeType: string
  assetUrl: string
  type: AssetType
  status: AssetStatus
  createdAt: string
  updatedAt: string
  keyMetadata: Record<string, MetadataValue>
  summary: MetadataSummary | null
  aiStatus: AiStatus
  rightsStatus: RightsStatus
  lastEnrichment: LastEnrichment | null
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface MetadataOrigin {
  proposalId: string
  agent: string | null
  aiModel: string | null
  confidence: number
  explanation: string | null
  evidence: string[]
  route: ApprovalRoute
  decidedBy: string | null
  decidedAt: string | null
  workflowRunId: string | null
  editedByReviewer: boolean
}

export interface MetadataEntry {
  field: string
  value: MetadataValue
  source: MetadataSource
  status: MetadataStatus
  confidence: number | null
  aiModel: string | null
  authoritative: boolean
  updatedAt: string
  updatedBy: string
  origin: MetadataOrigin | null
}

export interface AssetMetadata {
  assetId: string
  externalAssetId: string
  assetType: AssetType
  assetStatus: AssetStatus
  schemaVersion: number
  metadata: MetadataEntry[]
}

export interface ValidationRules {
  pattern: string | null
  minLength: number | null
  maxLength: number | null
  minValue: number | null
  maxValue: number | null
  maxItems: number | null
}

export interface FieldDefinition {
  key: string
  displayName: string
  dataType: MetadataDataType
  required: boolean
  repeatable: boolean
  searchable: boolean
  semanticSearch: boolean
  allowedValues: string[]
  validation: ValidationRules | null
  ai: { enrichable: boolean; autoApply: boolean; humanApprovalRequired: boolean }
  description: string
  group: string
}

export interface FieldGroup {
  key: string
  displayName: string
}

export interface SchemaRule {
  id: string
  type: string
  fields: string[]
  description: string
}

export interface MetadataSchema {
  version: number
  assetType: AssetType
  requiredFields: string[]
  recommendedFields: string[]
  fields: FieldDefinition[]
  rules: SchemaRule[]
  groups: FieldGroup[]
}

export interface Issue {
  severity: IssueSeverity
  code: string
  field: string | null
  message: string
}

export interface ValidationResult {
  assetId: string
  assetType: AssetType
  assetStatus: AssetStatus
  schemaVersion: number
  valid: boolean
  completenessPercentage: number
  requiredCompletenessPercentage: number
  missingRequiredFields: string[]
  missingRecommendedFields: string[]
  errors: Issue[]
  warnings: Issue[]
}

export interface AiSuggestion {
  field: string | null
  category: string
  severity: string
  message: string
  suggestedImprovement: string | null
}

export interface QualityReport {
  assetId: string
  assetType: AssetType
  schemaVersion: number
  qualityScore: number
  completenessPercentage: number
  requiredCompletenessPercentage: number
  missingRequiredFields: string[]
  deterministicErrors: Issue[]
  warnings: Issue[]
  aiStatus: 'COMPLETED' | 'SKIPPED' | 'UNAVAILABLE'
  aiSuggestions: AiSuggestion[]
  confidence: number | null
  aiSummary: string | null
  aiModel: string | null
  assessedAt: string
}

export interface ReviewPolicy {
  requiresHumanApproval: boolean
  canAutoApprove: boolean
  bulkApprovable: boolean
  riskLevel: RiskLevel
  riskFactors: string[]
}

export interface ProposalFieldInfo {
  definedInSchema: boolean
  displayName: string
  dataType: MetadataDataType | null
  group: string | null
  repeatable: boolean
  allowedValues: string[]
}

export interface Proposal {
  id: string
  assetId: string
  externalAssetId: string | null
  assetType: AssetType | null
  assetFilename: string | null
  field: string
  fieldInfo: ProposalFieldInfo
  proposedValue: MetadataValue
  currentValue: MetadataValue
  appliedValue: MetadataValue
  confidence: number
  explanation: string | null
  evidence: string[]
  source: MetadataSource
  proposedBy: string | null
  aiModel: string | null
  workflowRunId: string | null
  route: ApprovalRoute
  routeReason: string | null
  status: MetadataStatus
  createdAt: string
  decidedAt: string | null
  decidedBy: string | null
  decisionComment: string | null
  policy: ReviewPolicy
}

export interface BulkApprovalItem {
  proposalId: string
  field: string | null
  outcome: 'APPROVED' | 'SKIPPED' | 'FAILED'
  code: string | null
  message: string | null
}

export interface BulkApprovalResult {
  approved: number
  skipped: number
  failed: number
  items: BulkApprovalItem[]
}

export interface WorkflowStage {
  id: string
  name: string
  kind: StepKind | null
  description: string
  parallelGroup: string | null
  conditional: boolean
  status: StageStatus
  startedAt: string | null
  durationMillis: number | null
  inputTokens: number | null
  outputTokens: number | null
  toolCalls: string[]
  detail: string | null
  attempts: number
}

export interface RunSummary {
  autoApplied: number
  pendingReview: number
  lowConfidenceSuggestions: number
  rejectedByPolicy: number
  unchanged: number
}

export interface WorkflowRun {
  workflowId: string
  workflowType: string
  assetId: string
  externalAssetId: string | null
  status: RunStatus
  requestedBy: string
  aiModel: string | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
  durationMillis: number | null
  currentStage: string | null
  stages: WorkflowStage[]
  summary: RunSummary | null
  warnings: string[]
  error: { code: string; message: string } | null
}

export interface StageDefinition {
  id: string
  displayName: string
  kind: StepKind
  description: string
  parallelGroup: string | null
  conditional: boolean
}

export interface SearchCriteria {
  filters: Record<string, string>
  semanticQuery: string | null
  rightsExpiryAfter: string | null
  rightsExpiryBefore: string | null
  missingFields: string[]
  assetType: AssetType | null
  similarToAssetId: string | null
  limit: number | null
}

export interface SearchHit {
  id: string
  externalAssetId: string
  filename: string
  mimeType: string
  assetUrl: string
  type: AssetType
  status: AssetStatus
  similarity: number | null
  metadata: Record<string, MetadataValue>
}

export interface SearchResult {
  criteria: SearchCriteria
  semanticApplied: boolean
  degraded: boolean
  warnings: string[]
  count: number
  results: SearchHit[]
}

export interface StructuredSearchRequest {
  filters?: Record<string, string>
  semanticQuery?: string
  rightsExpiryBefore?: string
  missingFields?: string[]
  assetType?: AssetType
  similarToAssetId?: string
  limit?: number
}

export interface NaturalLanguageSearchRequest {
  query: string
  limit?: number
  filters?: Record<string, string>
  assetType?: AssetType
  rightsExpiryBefore?: string
}

export interface NaturalLanguageSearchResult {
  query: string
  interpretedByAi: boolean
  interpretationNotes: string | null
  interpretedCriteria: SearchCriteria
  search: SearchResult
}

export interface TaxonomyNode {
  id: string
  parentId: string | null
  name: string
  path: string
  depth: number
  leaf: boolean
  synonyms: string[]
}

export interface TaxonomyUsage {
  nodeId: string
  path: string
  assetCount: number
  totalAssetCount: number
}

export interface AuditEvent {
  id: string
  assetId: string
  externalAssetId: string | null
  field: string
  action: AuditAction
  previousValue: MetadataValue
  newValue: MetadataValue
  source: MetadataSource | null
  aiModel: string | null
  confidence: number | null
  actor: string
  approvedBy: string | null
  proposalId: string | null
  reason: string | null
  correlationId: string | null
  timestamp: string
}

export interface DistributionBucket {
  label: string
  min: number
  max: number
  count: number
}

export interface Dashboard {
  generatedAt: string
  assets: {
    total: number
    missingRequired: number
    averageQualityScore: number | null
    averageCompleteness: number | null
    byStatus: Partial<Record<AssetStatus, number>>
  }
  proposals: {
    pending: number
    pendingHumanReview: number
    pendingLowConfidence: number
    outcomes: {
      autoApproved: number
      humanApproved: number
      humanRejected: number
      rejectedByPolicy: number
      superseded: number
      pending: number
    }
  }
  rights: {
    warningWindowDays: number
    expiringWithin7Days: number
    expiringWithinWarningWindow: number
    expired: number
    missing: number
  }
  enrichment: { runsToday: number; failedToday: number; active: number; recentRuns: WorkflowRun[] }
  completenessDistribution: DistributionBucket[]
  qualityDistribution: DistributionBucket[]
}

export interface CapabilityStatus {
  state: CapabilityState
  detail: string | null
  provider: string | null
  model: string | null
  embeddingModel: string | null
  timeoutSeconds: number | null
  maxToolInvocations: number | null
}

export interface SystemStatus {
  checkedAt: string
  environment: string
  ai: CapabilityStatus
  vectorSearch: CapabilityStatus
  dam: CapabilityStatus
  governance: { autoApproveThreshold: number; humanReviewThreshold: number; schemaVersion: number }
  maxConcurrentEnrichments: number
}

export interface SimilarAsset {
  id: string
  externalAssetId: string
  filename: string
  similarity: number
  kind: 'PROBABLE_DUPLICATE' | 'SIMILAR'
  identicalFields: string[]
}

export interface Recommendation {
  field: string
  value: MetadataValue
  confidence: number
  supportingAssets: number
  supportingExternalAssetIds: string[]
  requiresHumanApproval: boolean
}

export interface KnowledgeSource {
  documentId: string
  title: string
  section: string
  text: string
  score: number
}

export interface KnowledgeAnswer {
  answer: string
  sources: KnowledgeSource[]
  grounded: boolean
  model: string
}

export interface BulkValidationItem {
  assetId: string
  externalAssetId: string | null
  valid: boolean
  assetStatus: AssetStatus | null
  errorCount: number
  warningCount: number
  completenessPercentage: number
  requiredCompletenessPercentage: number
  error: { code: string; message: string } | null
}

/** RFC 9457 problem details as produced by the backend (plus its stable `code`). */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  code?: string
  correlationId?: string
  issues?: Issue[]
  violations?: { field: string; message: string }[]
}
