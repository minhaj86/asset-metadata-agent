import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import { CheckCheck, Inbox, X } from 'lucide-react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { FilterSelect } from '@/components/FilterSelect'
import { PageHeader } from '@/components/PageHeader'
import { EmptyState, ErrorState } from '@/components/States'
import { fetchProposals, type ProposalParams } from '@/api/approvals'
import { queryKeys } from '@/api/queryKeys'
import { ApprovalQueueTable } from '@/features/approvals/ApprovalQueueTable'
import { ProposalReviewCard } from '@/features/approvals/ProposalReviewCard'
import { useBulkApprove } from '@/features/approvals/useProposalMutations'
import { useSchema } from '@/features/assets/assetQueries'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { useSystemStatus } from '@/hooks/useSystemStatus'
import type { Proposal, RiskLevel } from '@/types/api'

const AGENTS = [
  { value: 'metadataExtractionAgent', label: 'Metadata Extraction Agent' },
  { value: 'metadataEnrichmentAgent', label: 'Metadata Enrichment Agent' },
  { value: 'taxonomyAgent', label: 'Taxonomy Agent' },
]

export default function ApprovalsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { data: status } = useSystemStatus()
  const schema = useSchema('PRODUCT_IMAGE')
  const [assetText, setAssetText] = useState(searchParams.get('asset') ?? '')
  const debouncedAsset = useDebouncedValue(assetText)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [reviewing, setReviewing] = useState<Proposal | null>(null)
  const [confirmBulk, setConfirmBulk] = useState(false)
  const bulkApprove = useBulkApprove()

  const get = (key: string) => searchParams.get(key) ?? undefined
  const set = (key: string, value: string | undefined) =>
    setSearchParams(
      (previous) => {
        const next = new URLSearchParams(previous)
        if (value) {
          next.set(key, value)
        } else {
          next.delete(key)
        }
        return next
      },
      { replace: true },
    )

  const thresholds = status?.governance
  const confidenceBand = get('confidence')
  const params: ProposalParams = useMemo(() => {
    const band =
      thresholds && confidenceBand === 'high'
        ? { minConfidence: thresholds.autoApproveThreshold }
        : thresholds && confidenceBand === 'medium'
          ? { minConfidence: thresholds.humanReviewThreshold, maxConfidence: thresholds.autoApproveThreshold }
          : thresholds && confidenceBand === 'low'
            ? { maxConfidence: thresholds.humanReviewThreshold }
            : {}
    const createdAfter = searchParams.get('since')
    return {
      status: (searchParams.get('status') as ProposalParams['status']) ?? 'PROPOSED',
      asset: debouncedAsset || undefined,
      field: searchParams.get('field') ?? undefined,
      agent: searchParams.get('agent') ?? undefined,
      riskLevel: (searchParams.get('risk') as RiskLevel | null) ?? undefined,
      createdAfter: createdAfter ? new Date(`${createdAfter}T00:00:00`).toISOString() : undefined,
      limit: 500,
      ...band,
    }
  }, [searchParams, debouncedAsset, thresholds, confidenceBand])

  const proposals = useQuery({
    queryKey: queryKeys.proposalList(params as Record<string, unknown>),
    queryFn: ({ signal }) => fetchProposals(params, signal),
  })
  const rows = proposals.data ?? []
  const eligible = rows.filter((proposal) => proposal.status === 'PROPOSED' && proposal.policy.bulkApprovable)
  const selectedIds = [...selected].filter((id) => eligible.some((proposal) => proposal.id === id))
  const humanRequired = rows.filter((proposal) => proposal.policy.requiresHumanApproval).length

  const toggle = (proposalId: string) =>
    setSelected((previous) => {
      const next = new Set(previous)
      if (next.has(proposalId)) {
        next.delete(proposalId)
      } else {
        next.add(proposalId)
      }
      return next
    })

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="Approvals"
        description="Human-in-the-loop review of AI proposals. Backend policy decides which fields need a human decision and which proposals may be approved in bulk."
      />

      <div className="flex flex-wrap items-end gap-3 rounded-lg border bg-card p-3" role="search" aria-label="Filter proposals">
        <div className="flex w-40 flex-col gap-1">
          <Label htmlFor="approval-asset" className="text-xs text-muted-foreground">
            Asset
          </Label>
          <Input id="approval-asset" className="h-7" value={assetText} onChange={(event) => setAssetText(event.target.value)} placeholder="DA-100001" />
        </div>
        <FilterSelect
          label="Metadata field"
          value={get('field')}
          options={(schema.data?.fields ?? []).filter((field) => field.ai.enrichable).map((field) => ({ value: field.key, label: field.displayName }))}
          onChange={(value) => set('field', value)}
        />
        <FilterSelect
          label="Confidence"
          value={confidenceBand}
          options={[
            { value: 'high', label: 'High' },
            { value: 'medium', label: 'Medium' },
            { value: 'low', label: 'Low' },
          ]}
          onChange={(value) => set('confidence', value)}
        />
        <FilterSelect label="Agent" value={get('agent')} options={AGENTS} onChange={(value) => set('agent', value)} />
        <FilterSelect
          label="Risk level"
          value={get('risk')}
          options={[
            { value: 'HIGH', label: 'High' },
            { value: 'MEDIUM', label: 'Medium' },
            { value: 'LOW', label: 'Low' },
          ]}
          onChange={(value) => set('risk', value)}
        />
        <div className="flex w-40 flex-col gap-1">
          <Label htmlFor="approval-since" className="text-xs text-muted-foreground">
            Created since
          </Label>
          <Input id="approval-since" type="date" className="h-7" value={get('since') ?? ''} onChange={(event) => set('since', event.target.value || undefined)} />
        </div>
        <FilterSelect
          label="Status"
          value={get('status')}
          anyLabel="Open (awaiting decision)"
          options={[
            { value: 'APPROVED', label: 'Approved' },
            { value: 'REJECTED', label: 'Rejected' },
            { value: 'ALL', label: 'All' },
          ]}
          onChange={(value) => set('status', value)}
        />
      </div>

      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="text-muted-foreground">
          {rows.length} proposal(s) · {humanRequired} require human approval by schema · {eligible.length} eligible for bulk approval
        </span>
        <div className="ml-auto flex flex-wrap gap-2">
          {eligible.length > 0 && (
            <Button variant="outline" size="sm" onClick={() => setSelected(new Set(eligible.map((proposal) => proposal.id)))}>
              Select all eligible ({eligible.length})
            </Button>
          )}
          {selectedIds.length > 0 && (
            <>
              <Button variant="ghost" size="sm" onClick={() => setSelected(new Set())}>
                <X aria-hidden />
                Clear
              </Button>
              <Button size="sm" onClick={() => setConfirmBulk(true)} disabled={bulkApprove.isPending}>
                <CheckCheck aria-hidden />
                Approve {selectedIds.length} selected
              </Button>
            </>
          )}
        </div>
      </div>

      {proposals.isPending ? (
        <Skeleton className="h-64 w-full" aria-label="Loading proposals" />
      ) : proposals.isError ? (
        <ErrorState error={proposals.error} onRetry={() => void proposals.refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState icon={Inbox} title="Nothing to review" description="No proposals match these filters." />
      ) : (
        <ApprovalQueueTable proposals={rows} selected={selected} onToggle={toggle} onReview={setReviewing} />
      )}

      <Sheet open={reviewing !== null} onOpenChange={(open) => !open && setReviewing(null)}>
        <SheetContent side="right" className="w-full overflow-y-auto data-[side=right]:sm:max-w-2xl">
          <SheetHeader className="border-b">
            <SheetTitle>Review AI suggestion</SheetTitle>
            <SheetDescription>
              {reviewing?.externalAssetId} · {reviewing?.assetFilename}
            </SheetDescription>
          </SheetHeader>
          <div className="px-4 pb-6">{reviewing && <ProposalReviewCard proposal={reviewing} showAsset onDecided={() => setReviewing(null)} />}</div>
        </SheetContent>
      </Sheet>

      <AlertDialog open={confirmBulk} onOpenChange={setConfirmBulk}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Approve {selectedIds.length} proposal(s)?</AlertDialogTitle>
            <AlertDialogDescription>
              Only proposals the backend policy marks as low-risk and auto-approvable are applied. The backend re-checks every
              proposal and skips any that need individual review. Each approval is audited under your identity.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={() => bulkApprove.mutate({ proposalIds: selectedIds }, { onSuccess: () => setSelected(new Set()) })}>
              Approve
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
