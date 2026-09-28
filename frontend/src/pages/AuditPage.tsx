import { useMemo, useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import { History } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { FilterSelect } from '@/components/FilterSelect'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { EmptyState, ErrorState } from '@/components/States'
import { fetchAuditEvents, type AuditParams } from '@/api/audit'
import { queryKeys } from '@/api/queryKeys'
import { useSchema } from '@/features/assets/assetQueries'
import { AuditTable } from '@/features/audit/AuditTable'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import type { AuditAction, MetadataSource } from '@/types/api'
import { humanize } from '@/utils/format'

const SOURCES: MetadataSource[] = ['MANUAL', 'DAM', 'PRODUCT_SYSTEM', 'EMBEDDED', 'DERIVED', 'AI_GENERATED']
const ACTIONS: AuditAction[] = [
  'VALUE_IMPORTED',
  'VALUE_SET_MANUALLY',
  'VALUE_REMOVED',
  'VALUE_AUTO_APPLIED',
  'PROPOSAL_APPROVED',
  'PROPOSAL_REJECTED',
  'PROPOSAL_SUPERSEDED',
]
const AGENTS = ['metadataExtractionAgent', 'metadataEnrichmentAgent', 'taxonomyAgent']
const PAGE_SIZE = 50

export default function AuditPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const schema = useSchema('PRODUCT_IMAGE')
  const [assetText, setAssetText] = useState(searchParams.get('asset') ?? '')
  const [userText, setUserText] = useState(searchParams.get('user') ?? '')
  const asset = useDebouncedValue(assetText)
  const user = useDebouncedValue(userText)

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
        if (key !== 'page') {
          next.delete('page')
        }
        return next
      },
      { replace: true },
    )

  const page = Number(searchParams.get('page') ?? 0) || 0
  const agent = get('agent')
  const params: AuditParams = useMemo(() => {
    const from = searchParams.get('from')
    const to = searchParams.get('to')
    return {
      asset: asset || undefined,
      field: searchParams.get('field') ?? undefined,
      source: (searchParams.get('source') as MetadataSource | null) ?? undefined,
      actor: agent ? `ai:${agent}` : user || undefined,
      actorType: (searchParams.get('actorType') as AuditParams['actorType'] | null) ?? undefined,
      action: (searchParams.get('action') as AuditAction | null) ?? undefined,
      from: from ? new Date(`${from}T00:00:00`).toISOString() : undefined,
      to: to ? new Date(`${to}T23:59:59.999`).toISOString() : undefined,
      page,
      size: PAGE_SIZE,
    }
  }, [searchParams, asset, user, agent, page])

  const events = useQuery({
    queryKey: queryKeys.audit(params as Record<string, unknown>),
    queryFn: ({ signal }) => fetchAuditEvents(params, signal),
    placeholderData: keepPreviousData,
  })

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="Audit"
        description="Append-only history of every metadata change and approval decision: previous and new value, source, AI model, confidence, actor and reason."
      />
      <div className="flex flex-wrap items-end gap-3 rounded-lg border bg-card p-3" role="search" aria-label="Filter audit events">
        <div className="flex w-36 flex-col gap-1">
          <Label htmlFor="audit-asset" className="text-xs text-muted-foreground">
            Asset
          </Label>
          <Input id="audit-asset" className="h-7" value={assetText} onChange={(event) => setAssetText(event.target.value)} placeholder="DA-100001" />
        </div>
        <FilterSelect
          label="Field"
          value={get('field')}
          options={(schema.data?.fields ?? []).map((field) => ({ value: field.key, label: field.displayName }))}
          onChange={(value) => set('field', value)}
        />
        <FilterSelect label="Source" value={get('source')} options={SOURCES.map((source) => ({ value: source, label: humanize(source) }))} onChange={(value) => set('source', value)} />
        <div className="flex w-36 flex-col gap-1">
          <Label htmlFor="audit-user" className="text-xs text-muted-foreground">
            User
          </Label>
          <Input id="audit-user" className="h-7" value={userText} onChange={(event) => setUserText(event.target.value)} placeholder="maria.reviewer" disabled={Boolean(agent)} />
        </div>
        <FilterSelect
          label="Agent"
          value={agent}
          options={AGENTS.map((name) => ({ value: name, label: humanize(name) }))}
          onChange={(value) => set('agent', value)}
        />
        <FilterSelect
          label="Actor type"
          value={get('actorType')}
          options={[
            { value: 'HUMAN', label: 'Human' },
            { value: 'AI', label: 'AI agent' },
            { value: 'SYSTEM', label: 'System / policy' },
          ]}
          onChange={(value) => set('actorType', value)}
        />
        <FilterSelect label="Action" value={get('action')} options={ACTIONS.map((action) => ({ value: action, label: humanize(action) }))} onChange={(value) => set('action', value)} />
        <div className="flex w-36 flex-col gap-1">
          <Label htmlFor="audit-from" className="text-xs text-muted-foreground">
            From
          </Label>
          <Input id="audit-from" type="date" className="h-7" value={get('from') ?? ''} onChange={(event) => set('from', event.target.value || undefined)} />
        </div>
        <div className="flex w-36 flex-col gap-1">
          <Label htmlFor="audit-to" className="text-xs text-muted-foreground">
            To
          </Label>
          <Input id="audit-to" type="date" className="h-7" value={get('to') ?? ''} onChange={(event) => set('to', event.target.value || undefined)} />
        </div>
      </div>

      {events.isPending ? (
        <Skeleton className="h-96 w-full" aria-label="Loading audit events" />
      ) : events.isError ? (
        <ErrorState error={events.error} onRetry={() => void events.refetch()} />
      ) : events.data.items.length === 0 ? (
        <EmptyState icon={History} title="No audit events match these filters" />
      ) : (
        <>
          <AuditTable events={events.data.items} />
          <Pagination
            page={events.data.page}
            totalPages={events.data.totalPages}
            totalItems={events.data.totalItems}
            pageSize={events.data.size}
            onPageChange={(next) => set('page', next > 0 ? String(next) : undefined)}
            label="events"
          />
        </>
      )}
    </div>
  )
}
