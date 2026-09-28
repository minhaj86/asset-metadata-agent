import type {
  AssetMetadata,
  CatalogItem,
  Dashboard,
  FieldDefinition,
  MetadataEntry,
  MetadataSchema,
  Page,
  Proposal,
  QualityReport,
  SystemStatus,
  WorkflowRun,
  WorkflowStage,
} from '@/types/api'

export const ASSET_ID = '8823521b-c5bb-4efe-bb45-413c244d5c2f'
export const WORKFLOW_ID = '76668fc0-9589-4e39-8764-99dcb3e51992'

export function aSystemStatus(overrides: Partial<SystemStatus> = {}): SystemStatus {
  return {
    checkedAt: '2026-10-03T10:00:00Z',
    environment: 'local',
    ai: { state: 'AVAILABLE', detail: null, provider: 'demo', model: 'offline-demo-rules-v1', embeddingModel: 'all-minilm-l6-v2', timeoutSeconds: 180, maxToolInvocations: 5 },
    vectorSearch: { state: 'AVAILABLE', detail: '16 of 16 assets indexed', provider: null, model: null, embeddingModel: 'all-minilm-l6-v2', timeoutSeconds: null, maxToolInvocations: null },
    dam: { state: 'AVAILABLE', detail: '19 catalog assets', provider: 'Mock DAM', model: null, embeddingModel: null, timeoutSeconds: null, maxToolInvocations: null },
    governance: { autoApproveThreshold: 0.92, humanReviewThreshold: 0.7, schemaVersion: 2 },
    maxConcurrentEnrichments: 2,
    ...overrides,
  }
}

export function aCatalogItem(overrides: Partial<CatalogItem> = {}): CatalogItem {
  return {
    id: ASSET_ID,
    externalAssetId: 'DA-100001',
    filename: 'blue-shirt-front.jpg',
    mimeType: 'image/jpeg',
    assetUrl: 'http://localhost:8080/mock-dam/assets/DA-100001/blue-shirt-front.jpg',
    type: 'PRODUCT_IMAGE',
    status: 'DRAFT',
    createdAt: '2026-10-03T09:00:00Z',
    updatedAt: '2026-10-03T09:30:00Z',
    keyMetadata: { productId: '12345', productType: 'SHIRT', color: 'BLUE' },
    summary: {
      valid: false,
      completeness: 42.9,
      requiredCompleteness: 75,
      qualityScore: 53,
      missingRequiredFields: ['angleFacing', 'altText'],
      errorCount: 2,
      warningCount: 6,
      aiValueCount: 0,
      pendingReviewCount: 0,
      lowConfidenceCount: 0,
      usageRightsEnd: '2027-12-31',
      refreshedAt: '2026-10-03T09:30:00Z',
    },
    aiStatus: 'NOT_ENRICHED',
    rightsStatus: 'VALID',
    lastEnrichment: null,
    ...overrides,
  }
}

export function aPage<T>(items: T[], overrides: Partial<Page<T>> = {}): Page<T> {
  return { items, page: 0, size: 25, totalItems: items.length, totalPages: items.length ? 1 : 0, ...overrides }
}

export function aFieldDefinition(key: string, overrides: Partial<FieldDefinition> = {}): FieldDefinition {
  return {
    key,
    displayName: key,
    dataType: 'STRING',
    required: false,
    repeatable: false,
    searchable: true,
    semanticSearch: false,
    allowedValues: [],
    validation: null,
    ai: { enrichable: true, autoApply: false, humanApprovalRequired: false },
    description: '',
    group: 'PRODUCT',
    ...overrides,
  }
}

export function aSchema(): MetadataSchema {
  return {
    version: 2,
    assetType: 'PRODUCT_IMAGE',
    requiredFields: ['productId', 'productType', 'color', 'angleFacing', 'altText', 'usageRightsEnd'],
    recommendedFields: ['title', 'keywords', 'taxonomyPath'],
    groups: [
      { key: 'PRODUCT', displayName: 'Product' },
      { key: 'VISUAL', displayName: 'Visual' },
      { key: 'CONTENT', displayName: 'Content' },
      { key: 'RIGHTS', displayName: 'Rights' },
    ],
    rules: [],
    fields: [
      aFieldDefinition('productId', { displayName: 'Product ID', required: true, ai: { enrichable: false, autoApply: false, humanApprovalRequired: true }, description: 'Identifier of the product.' }),
      aFieldDefinition('productType', { displayName: 'Product type', required: true, allowedValues: ['SHIRT', 'DRESS'], ai: { enrichable: true, autoApply: true, humanApprovalRequired: false } }),
      aFieldDefinition('color', { displayName: 'Colour', required: true, allowedValues: ['BLUE', 'RED', 'WHITE'], ai: { enrichable: true, autoApply: true, humanApprovalRequired: false } }),
      aFieldDefinition('taxonomyPath', { displayName: 'Taxonomy path', dataType: 'TAXONOMY_PATH', ai: { enrichable: true, autoApply: false, humanApprovalRequired: true } }),
      aFieldDefinition('angleFacing', { displayName: 'Angle facing', group: 'VISUAL', required: true, allowedValues: ['FRONT', 'BACK', 'LEFT', 'RIGHT', 'DETAIL'], ai: { enrichable: true, autoApply: false, humanApprovalRequired: true }, description: 'Which side of the product faces the camera.' }),
      aFieldDefinition('backgroundType', { displayName: 'Background type', group: 'VISUAL', allowedValues: ['WHITE', 'GREY'], ai: { enrichable: true, autoApply: true, humanApprovalRequired: false } }),
      aFieldDefinition('title', { displayName: 'Title', group: 'CONTENT', dataType: 'TEXT' }),
      aFieldDefinition('altText', { displayName: 'Alt text', group: 'CONTENT', dataType: 'TEXT', required: true, validation: { pattern: null, minLength: 15, maxLength: 250, minValue: null, maxValue: null, maxItems: null } }),
      aFieldDefinition('keywords', { displayName: 'Keywords', group: 'CONTENT', repeatable: true, ai: { enrichable: true, autoApply: true, humanApprovalRequired: false } }),
      aFieldDefinition('usageRightsEnd', { displayName: 'Usage rights end', group: 'RIGHTS', dataType: 'DATE', required: true, ai: { enrichable: false, autoApply: false, humanApprovalRequired: true } }),
    ],
  }
}

export function anEntry(overrides: Partial<MetadataEntry> = {}): MetadataEntry {
  return {
    field: 'productId',
    value: '12345',
    source: 'PRODUCT_SYSTEM',
    status: 'APPROVED',
    confidence: null,
    aiModel: null,
    authoritative: true,
    updatedAt: '2026-10-03T09:00:00Z',
    updatedBy: 'system:sample-data-loader',
    origin: null,
    ...overrides,
  }
}

export function anAssetMetadata(entries: MetadataEntry[] = defaultEntries()): AssetMetadata {
  return {
    assetId: ASSET_ID,
    externalAssetId: 'DA-100001',
    assetType: 'PRODUCT_IMAGE',
    assetStatus: 'DRAFT',
    schemaVersion: 2,
    metadata: entries,
  }
}

export function defaultEntries(): MetadataEntry[] {
  return [
    anEntry(),
    anEntry({ field: 'productType', value: 'SHIRT', source: 'DAM' }),
    anEntry({
      field: 'backgroundType',
      value: 'WHITE',
      source: 'AI_GENERATED',
      status: 'VALIDATED',
      authoritative: false,
      confidence: 0.96,
      aiModel: 'demo:offline-demo-rules-v1',
      updatedBy: 'ai:metadataExtractionAgent',
      origin: {
        proposalId: 'p-background',
        agent: 'metadataExtractionAgent',
        aiModel: 'demo:offline-demo-rules-v1',
        confidence: 0.96,
        explanation: 'Image analysis reports a plain white studio background.',
        evidence: ['imageAnalysis.background=white'],
        route: 'AUTO_APPROVE',
        decidedBy: 'system:approval-policy',
        decidedAt: '2026-10-03T09:30:00Z',
        workflowRunId: WORKFLOW_ID,
        editedByReviewer: false,
      },
    }),
  ]
}

export function aProposal(overrides: Partial<Proposal> = {}): Proposal {
  return {
    id: 'p-angle',
    assetId: ASSET_ID,
    externalAssetId: 'DA-100001',
    assetType: 'PRODUCT_IMAGE',
    assetFilename: 'blue-shirt-front.jpg',
    field: 'angleFacing',
    fieldInfo: { definedInSchema: true, displayName: 'Angle facing', dataType: 'STRING', group: 'VISUAL', repeatable: false, allowedValues: ['FRONT', 'BACK', 'LEFT', 'RIGHT', 'DETAIL'] },
    proposedValue: 'FRONT',
    currentValue: null,
    appliedValue: null,
    confidence: 0.96,
    explanation: 'The detected product orientation is front-facing.',
    evidence: ['image analysis orientation: FRONT', 'filename contains "-front"'],
    source: 'AI_GENERATED',
    proposedBy: 'metadataExtractionAgent',
    aiModel: 'demo:offline-demo-rules-v1',
    workflowRunId: WORKFLOW_ID,
    route: 'HUMAN_REVIEW',
    routeReason: "Field 'angleFacing' requires human approval",
    status: 'PROPOSED',
    createdAt: '2026-10-03T09:30:00Z',
    decidedAt: null,
    decidedBy: null,
    decisionComment: null,
    policy: { requiresHumanApproval: true, canAutoApprove: false, bulkApprovable: false, riskLevel: 'HIGH', riskFactors: ['Sensitive field: the schema requires human approval'] },
    ...overrides,
  }
}

export function aStage(id: string, overrides: Partial<WorkflowStage> = {}): WorkflowStage {
  return {
    id,
    name: id,
    kind: 'AI_AGENT',
    description: '',
    parallelGroup: null,
    conditional: false,
    status: 'WAITING',
    startedAt: null,
    durationMillis: null,
    inputTokens: null,
    outputTokens: null,
    toolCalls: [],
    detail: null,
    attempts: 0,
    ...overrides,
  }
}

export function aWorkflowRun(overrides: Partial<WorkflowRun> = {}): WorkflowRun {
  return {
    workflowId: WORKFLOW_ID,
    workflowType: 'METADATA_ENRICHMENT',
    assetId: ASSET_ID,
    externalAssetId: 'DA-100001',
    status: 'RUNNING',
    requestedBy: 'jane.doe',
    aiModel: 'demo:offline-demo-rules-v1',
    createdAt: '2026-10-03T09:30:00Z',
    startedAt: '2026-10-03T09:30:00Z',
    finishedAt: null,
    durationMillis: null,
    currentStage: 'Metadata Extraction Agent',
    stages: [
      aStage('loadAssetContext', { name: 'Load asset context', kind: 'DETERMINISTIC_STEP', status: 'COMPLETED', durationMillis: 21 }),
      aStage('metadataExtractionAgent', { name: 'Metadata Extraction Agent', status: 'RUNNING', attempts: 1 }),
      aStage('taxonomyAgent', { name: 'Taxonomy Agent', parallelGroup: 'parallelReasoning', conditional: true }),
      aStage('metadataEnrichmentAgent', { name: 'Metadata Enrichment Agent', parallelGroup: 'parallelReasoning' }),
      aStage('recordResults', { name: 'Apply policy & record proposals', kind: 'DETERMINISTIC_STEP' }),
    ],
    summary: null,
    warnings: [],
    error: null,
    ...overrides,
  }
}

export function aQualityReport(overrides: Partial<QualityReport> = {}): QualityReport {
  return {
    assetId: ASSET_ID,
    assetType: 'PRODUCT_IMAGE',
    schemaVersion: 2,
    qualityScore: 53,
    completenessPercentage: 42.9,
    requiredCompletenessPercentage: 75,
    missingRequiredFields: ['angleFacing', 'altText'],
    deterministicErrors: [
      { severity: 'ERROR', code: 'MISSING_REQUIRED_FIELD', field: 'angleFacing', message: "Required field 'angleFacing' is missing for PRODUCT_IMAGE" },
    ],
    warnings: [{ severity: 'WARNING', code: 'MISSING_RECOMMENDED_FIELD', field: 'title', message: "Recommended field 'title' is missing" }],
    aiStatus: 'SKIPPED',
    aiSuggestions: [],
    confidence: null,
    aiSummary: null,
    aiModel: null,
    assessedAt: '2026-10-03T09:30:00Z',
    ...overrides,
  }
}

export function aDashboard(): Dashboard {
  return {
    generatedAt: '2026-10-03T10:00:00Z',
    assets: { total: 16, missingRequired: 4, averageQualityScore: 79.9, averageCompleteness: 75.4, byStatus: { DRAFT: 5, READY: 11 } },
    proposals: {
      pending: 8,
      pendingHumanReview: 8,
      pendingLowConfidence: 0,
      outcomes: { autoApproved: 4, humanApproved: 1, humanRejected: 1, rejectedByPolicy: 0, superseded: 0, pending: 8 },
    },
    rights: { warningWindowDays: 30, expiringWithin7Days: 1, expiringWithinWarningWindow: 3, expired: 1, missing: 2 },
    enrichment: { runsToday: 1, failedToday: 0, active: 0, recentRuns: [aWorkflowRun({ status: 'COMPLETED', currentStage: null })] },
    completenessDistribution: [
      { label: '95-100%', min: 95, max: 100, count: 2 },
      { label: '80-94%', min: 80, max: 94, count: 4 },
      { label: '60-79%', min: 60, max: 79, count: 6 },
      { label: '<60%', min: 0, max: 59, count: 4 },
    ],
    qualityDistribution: [
      { label: '90-100%', min: 90, max: 100, count: 5 },
      { label: '70-89%', min: 70, max: 89, count: 7 },
      { label: '50-69%', min: 50, max: 69, count: 3 },
      { label: '<50%', min: 0, max: 49, count: 1 },
    ],
  }
}
