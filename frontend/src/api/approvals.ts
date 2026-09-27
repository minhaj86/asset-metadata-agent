import type { ApprovalRoute, BulkApprovalResult, Proposal, RiskLevel } from '@/types/api'
import { apiFetch } from './client'

export interface ProposalParams {
  status?: 'PROPOSED' | 'APPROVED' | 'REJECTED' | 'ALL'
  route?: ApprovalRoute
  assetId?: string
  /** substring of the external asset id */
  asset?: string
  workflowRunId?: string
  field?: string
  agent?: string
  minConfidence?: number
  maxConfidence?: number
  createdAfter?: string
  riskLevel?: RiskLevel
  limit?: number
}

export function fetchProposals(params: ProposalParams, signal?: AbortSignal): Promise<Proposal[]> {
  return apiFetch('/api/metadata/proposals', { query: { ...params }, signal })
}

export function fetchProposal(proposalId: string, signal?: AbortSignal): Promise<Proposal> {
  return apiFetch(`/api/metadata/proposals/${proposalId}`, { signal })
}

/** Approves a proposal; `value` applies the reviewer's edit instead of the proposed value. */
export function approveProposal(proposalId: string, input: { comment?: string; value?: unknown } = {}): Promise<Proposal> {
  return apiFetch(`/api/metadata/proposals/${proposalId}/approve`, {
    method: 'POST',
    body: { comment: input.comment || undefined, value: input.value },
  })
}

export function rejectProposal(proposalId: string, reason: string): Promise<Proposal> {
  return apiFetch(`/api/metadata/proposals/${proposalId}/reject`, { method: 'POST', body: { reason } })
}

/** The backend applies only proposals its policy marks bulk-approvable and reports the rest as skipped. */
export function bulkApproveProposals(proposalIds: string[], comment?: string): Promise<BulkApprovalResult> {
  return apiFetch('/api/metadata/proposals/bulk-approve', {
    method: 'POST',
    body: { proposalIds, comment: comment || undefined },
  })
}
