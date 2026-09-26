import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, Asterisk, Lightbulb, PanelRightOpen, Pencil, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { ConfidenceIndicator } from '@/components/ConfidenceIndicator'
import { MetadataValueView } from '@/components/MetadataValueView'
import { Section } from '@/components/PageHeader'
import { AuthorityBadge, Pill, SourceBadge } from '@/components/status/StatusBadges'
import { fetchQuality } from '@/api/metadata'
import { queryKeys } from '@/api/queryKeys'
import type { AssetMetadata, CatalogItem, FieldDefinition, Issue, MetadataSchema, Proposal } from '@/types/api'
import { formatDateTime, formatRelative, isEmptyValue } from '@/utils/format'
import { EditFieldDialog } from './EditFieldDialog'
import { FieldInspector } from './FieldInspector'

type FieldFilter = 'all' | 'set' | 'missing' | 'attention'

interface MetadataEditorProps {
  asset: CatalogItem
  schema: MetadataSchema
  metadata: AssetMetadata
  openProposals: Proposal[]
}

/**
 * Grouped metadata editor. Groups and field semantics come from the configurable schema; every value shows its
 * provenance so authoritative metadata and AI-generated values are always distinguishable.
 */
export function MetadataEditor({ asset, schema, metadata, openProposals }: MetadataEditorProps) {
  const [filter, setFilter] = useState<FieldFilter>('all')
  const [inspected, setInspected] = useState<FieldDefinition | null>(null)
  const [editing, setEditing] = useState<FieldDefinition | null>(null)
  const quality = useQuery({
    queryKey: queryKeys.quality(asset.id, false),
    queryFn: ({ signal }) => fetchQuality(asset.id, false, signal),
  })

  const entries = useMemo(() => new Map(metadata.metadata.map((entry) => [entry.field, entry])), [metadata])
  const proposals = useMemo(() => new Map(openProposals.map((proposal) => [proposal.field, proposal])), [openProposals])
  const issuesByField = useMemo(() => {
    const issues = new Map<string, Issue[]>()
    ;[...(quality.data?.deterministicErrors ?? []), ...(quality.data?.warnings ?? [])].forEach((issue) => {
      if (issue.field && issue.code !== 'MISSING_RECOMMENDED_FIELD') {
        issues.set(issue.field, [...(issues.get(issue.field) ?? []), issue])
      }
    })
    return issues
  }, [quality.data])

  const visible = (field: FieldDefinition) => {
    const set = !isEmptyValue(entries.get(field.key)?.value)
    switch (filter) {
      case 'set':
        return set
      case 'missing':
        return !set
      case 'attention':
        return issuesByField.has(field.key) || proposals.has(field.key) || (field.required && !set)
      default:
        return true
    }
  }

  const groups = schema.groups
    .map((group) => ({ group, fields: schema.fields.filter((field) => field.group === group.key && visible(field)) }))
    .filter(({ fields }) => fields.length > 0)

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">
          Schema v{schema.version} · {schema.fields.length} fields for {asset.type.replaceAll('_', ' ').toLowerCase()} ·{' '}
          <span className="inline-flex items-center gap-0.5">
            <Asterisk aria-hidden className="size-3" /> required
          </span>
        </p>
        <ToggleGroup type="single" variant="outline" size="sm" value={filter} onValueChange={(value) => value && setFilter(value as FieldFilter)} aria-label="Show fields">
          <ToggleGroupItem value="all">All</ToggleGroupItem>
          <ToggleGroupItem value="set">Set</ToggleGroupItem>
          <ToggleGroupItem value="missing">Missing</ToggleGroupItem>
          <ToggleGroupItem value="attention">Needs attention</ToggleGroupItem>
        </ToggleGroup>
      </div>

      {groups.map(({ group, fields }) => (
        <Section key={group.key} id={`group-${group.key}`} title={group.displayName} contentClassName="p-0">
          <Table className="text-[13px]">
            <TableHeader className="bg-muted/30">
              <TableRow>
                <TableHead className="w-56">Field</TableHead>
                <TableHead>Value</TableHead>
                <TableHead className="w-72">Provenance</TableHead>
                <TableHead className="w-36">Last updated</TableHead>
                <TableHead className="w-24 text-right">
                  <span className="sr-only">Actions</span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {fields.map((field) => {
                const entry = entries.get(field.key)
                const proposal = proposals.get(field.key)
                const issues = issuesByField.get(field.key) ?? []
                return (
                  <TableRow key={field.key} className="align-top">
                    <TableCell className="py-2">
                      <button
                        type="button"
                        onClick={() => setInspected(field)}
                        className="group flex flex-col items-start rounded-sm text-left outline-none focus-visible:ring-2 focus-visible:ring-ring"
                        aria-label={`Inspect ${field.displayName}`}
                      >
                        <span className="flex items-center gap-1 font-medium group-hover:underline">
                          {field.displayName}
                          {field.required && <Asterisk aria-label="required" className="size-3 text-red-600" />}
                        </span>
                        <span className="font-mono text-[11px] text-muted-foreground">{field.key}</span>
                      </button>
                    </TableCell>
                    <TableCell className="py-2 whitespace-normal">
                      <div className="flex flex-col gap-1.5">
                        <MetadataValueView value={entry?.value} dataType={field.dataType} clamp />
                        {issues.map((issue) => (
                          <span
                            key={`${issue.code}-${issue.message}`}
                            className={issue.severity === 'ERROR' ? 'flex items-center gap-1 text-xs text-red-700 dark:text-red-300' : 'flex items-center gap-1 text-xs text-amber-800 dark:text-amber-300'}
                          >
                            {issue.severity === 'ERROR' ? <XCircle aria-hidden className="size-3" /> : <AlertTriangle aria-hidden className="size-3" />}
                            {issue.message}
                          </span>
                        ))}
                        {proposal && (
                          <button
                            type="button"
                            onClick={() => setInspected(field)}
                            className="flex w-fit items-center gap-1.5 rounded-md border border-amber-200 bg-amber-50 px-1.5 py-0.5 text-xs text-amber-900 hover:bg-amber-100 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200"
                          >
                            <Lightbulb aria-hidden className="size-3" />
                            AI suggestion pending review
                          </button>
                        )}
                      </div>
                    </TableCell>
                    <TableCell className="py-2 whitespace-normal">
                      {entry ? (
                        <div className="flex flex-wrap items-center gap-1.5">
                          <SourceBadge source={entry.source} />
                          <AuthorityBadge authoritative={entry.authoritative} />
                          {entry.confidence !== null && <ConfidenceIndicator confidence={entry.confidence} variant="compact" />}
                          {entry.origin?.editedByReviewer && <Pill tone="info">Edited AI suggestion</Pill>}
                        </div>
                      ) : (
                        <span className="text-xs text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell className="py-2 text-xs text-muted-foreground">
                      {entry ? (
                        <span title={formatDateTime(entry.updatedAt)}>
                          {formatRelative(entry.updatedAt)}
                          <br />
                          <span className="font-mono">{entry.updatedBy}</span>
                        </span>
                      ) : (
                        '—'
                      )}
                    </TableCell>
                    <TableCell className="py-1.5 text-right">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon-sm" onClick={() => setEditing(field)} aria-label={`Edit ${field.displayName}`}>
                          <Pencil aria-hidden />
                        </Button>
                        <Button variant="ghost" size="icon-sm" onClick={() => setInspected(field)} aria-label={`Details of ${field.displayName}`}>
                          <PanelRightOpen aria-hidden />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </Section>
      ))}

      <Section title="System" description="Asset reference and record timestamps (managed by the platform)">
        <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm md:grid-cols-4">
          <div>
            <dt className="text-xs text-muted-foreground">Internal ID</dt>
            <dd className="font-mono text-xs break-all">{asset.id}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">MIME type</dt>
            <dd>{asset.mimeType}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Registered</dt>
            <dd>{formatDateTime(asset.createdAt)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Last modified</dt>
            <dd>{formatDateTime(asset.updatedAt)}</dd>
          </div>
        </dl>
      </Section>

      <FieldInspector
        assetId={asset.id}
        field={inspected}
        entry={inspected ? entries.get(inspected.key) : undefined}
        proposal={inspected ? proposals.get(inspected.key) : undefined}
        issues={inspected ? (issuesByField.get(inspected.key) ?? []) : []}
        onClose={() => setInspected(null)}
        onEdit={(field) => {
          setInspected(null)
          setEditing(field)
        }}
      />
      {editing && (
        <EditFieldDialog
          key={editing.key}
          assetId={asset.id}
          field={editing}
          entry={entries.get(editing.key)}
          open
          onOpenChange={(open) => !open && setEditing(null)}
        />
      )}
    </div>
  )
}
