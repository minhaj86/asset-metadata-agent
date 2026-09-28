import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { Proposal } from '@/types/api'
import { aProposal, ASSET_ID } from '../fixtures'
import { renderRoute } from '../render'
import { server } from '../server'

const safeCategory = aProposal({
  id: 'p-category',
  field: 'category',
  fieldInfo: { definedInSchema: true, displayName: 'Category', dataType: 'STRING', group: 'PRODUCT', repeatable: false, allowedValues: ['TOPS'] },
  proposedValue: 'TOPS',
  confidence: 0.81,
  proposedBy: 'metadataEnrichmentAgent',
  routeReason: 'Confidence 0.81 is below the auto-approval threshold 0.92',
  policy: { requiresHumanApproval: false, canAutoApprove: true, bulkApprovable: true, riskLevel: 'LOW', riskFactors: [] },
})

describe('AI proposal review', () => {
  it('shows what exists, what AI proposes, confidence, evidence and the backend policy', async () => {
    server.use(http.get('/api/metadata/proposals', () => HttpResponse.json([aProposal()])))

    renderRoute(`/assets/${ASSET_ID}?tab=suggestions`)

    const card = (await screen.findByRole('heading', { name: /Angle facing/i })).closest('article')!
    expect(within(card).getByText('96%')).toBeInTheDocument()
    expect(within(card).getByText('High confidence')).toBeInTheDocument()
    expect(within(card).getByText('Human approval required')).toBeInTheDocument()
    expect(within(card).getByText('High risk')).toBeInTheDocument()
    expect(within(card).getByText('The detected product orientation is front-facing.')).toBeInTheDocument()
    expect(within(card).getByText('filename contains "-front"')).toBeInTheDocument()
    expect(within(card).getByText('Current').nextElementSibling).toHaveTextContent('Not set')
    expect(within(card).getByText('AI proposed').nextElementSibling).toHaveTextContent('FRONT')
    expect(within(card).getByText('AI suggestion')).toBeInTheDocument()
    expect(card).not.toHaveTextContent(/AI (fixed|corrected)|guaranteed/i)
  })

  it('approves a proposal as the identified reviewer and refreshes the queue', async () => {
    let decided = false
    let reviewer: string | null = null
    server.use(
      http.get('/api/metadata/proposals', () => HttpResponse.json(decided ? [] : [aProposal()])),
      http.post('/api/metadata/proposals/:id/approve', ({ request }) => {
        decided = true
        reviewer = request.headers.get('X-User-Id')
        return HttpResponse.json(aProposal({ status: 'APPROVED', decidedBy: 'jane.doe', decidedAt: '2026-10-03T10:00:00Z' }))
      }),
    )
    const { user } = renderRoute('/approvals')

    await user.click(await screen.findByRole('button', { name: 'Review' }))
    const panel = await screen.findByRole('dialog')
    await user.click(within(panel).getByRole('button', { name: 'Approve' }))

    expect(await screen.findByText('Proposal approved')).toBeInTheDocument()
    expect(reviewer).toBe('jane.doe')
    expect(await screen.findByText('Nothing to review')).toBeInTheDocument()
  })

  it('requires a reason to reject and moves the proposal to the decided list', async () => {
    let current: Proposal = aProposal()
    let reason: unknown
    server.use(
      http.get('/api/metadata/proposals', () => HttpResponse.json([current])),
      http.post('/api/metadata/proposals/:id/reject', async ({ request }) => {
        reason = await request.json()
        current = aProposal({ status: 'REJECTED', decidedBy: 'jane.doe', decidedAt: '2026-10-03T10:00:00Z', decisionComment: 'Wrong angle' })
        return HttpResponse.json(current)
      }),
    )
    const { user } = renderRoute(`/assets/${ASSET_ID}?tab=suggestions`)

    const card = (await screen.findByRole('heading', { name: /Angle facing/i })).closest('article')!
    await user.click(within(card).getByRole('button', { name: 'Reject' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Reject proposal' }))
    expect(await within(dialog).findByText('Please give a reason (at least 3 characters)')).toBeInTheDocument()

    await user.type(within(dialog).getByRole('textbox', { name: 'Reason' }), 'Wrong angle')
    await user.click(within(dialog).getByRole('button', { name: 'Reject proposal' }))

    expect(await screen.findByText('Proposal rejected')).toBeInTheDocument()
    expect(reason).toEqual({ reason: 'Wrong angle' })
    expect(await screen.findByText('No AI suggestions awaiting review')).toBeInTheDocument()
    const decided = screen.getByRole('region', { name: /Decided suggestions/ })
    expect(within(decided).getByText('Rejected')).toBeInTheDocument()
  })

  it('only offers bulk approval for proposals the backend marks bulk-approvable', async () => {
    let approvedIds: unknown
    server.use(
      http.get('/api/metadata/proposals', () => HttpResponse.json([aProposal(), safeCategory])),
      http.post('/api/metadata/proposals/bulk-approve', async ({ request }) => {
        approvedIds = ((await request.json()) as { proposalIds: string[] }).proposalIds
        return HttpResponse.json({ approved: 1, skipped: 0, failed: 0, items: [{ proposalId: 'p-category', field: 'category', outcome: 'APPROVED', code: null, message: null }] })
      }),
    )
    const { user } = renderRoute('/approvals')

    expect(await screen.findByRole('checkbox', { name: /Select Category of DA-100001/ })).toBeEnabled()
    expect(screen.queryByRole('checkbox', { name: /Select Angle facing/ })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Requires individual review')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Select all eligible (1)' }))
    await user.click(screen.getByRole('button', { name: 'Approve 1 selected' }))
    await user.click(await screen.findByRole('button', { name: 'Approve' }))

    expect(await screen.findByText('Bulk approval completed')).toBeInTheDocument()
    expect(approvedIds).toEqual(['p-category'])
  })
})
