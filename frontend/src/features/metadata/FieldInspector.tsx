import { useRef } from 'react'
import { Link, useNavigate } from 'react-router'
import { History, Pencil, ShieldCheck, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { ConfidenceIndicator } from '@/components/ConfidenceIndicator'
import { KeyValueList } from '@/components/PageHeader'
import { MetadataValueView } from '@/components/MetadataValueView'
import { AuthorityBadge, MetadataStatusBadge, Pill, RouteBadge, SourceBadge } from '@/components/status/StatusBadges'
import { useStartEnrichment } from '@/features/enrichment/useStartEnrichment'
import { isAiAvailable, useSystemStatus } from '@/hooks/useSystemStatus'
import type { FieldDefinition, Issue, MetadataEntry, Proposal } from '@/types/api'
import { agentName, formatDateTime, humanize } from '@/utils/format'
import { FieldDefinitionDetails } from './FieldDefinitionDetails'
import { KnowledgeHint } from './KnowledgeHint'
import { useValidateAsset } from './useMetadataMutations'

interface FieldInspectorProps {
  assetId: string
  field: FieldDefinition | null
  entry: MetadataEntry | undefined
  proposal: Proposal | undefined
  issues: Issue[]
  onClose: () => void
  onEdit: (field: FieldDefinition) => void
}

/** Field definition, current value and full provenance; AI values additionally show agent, model and evidence. */
export function FieldInspector({ assetId, field, entry, proposal, issues, onClose, onEdit }: FieldInspectorProps) {
  const navigate = useNavigate()
  const titleRef = useRef<HTMLHeadingElement>(null)
  const validate = useValidateAsset(assetId)
  const enrich = useStartEnrichment()
  const { data: status } = useSystemStatus()

  return (
    <Sheet open={field !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent
        side="right"
        className="w-full overflow-y-auto data-[side=right]:sm:max-w-xl"
        onOpenAutoFocus={(event) => {
          // Start at the title rather than the first control (which would pop its tooltip open).
          event.preventDefault()
          titleRef.current?.focus()
        }}
      >
        {field && (
          <>
            <SheetHeader className="border-b">
              <SheetTitle ref={titleRef} tabIndex={-1} className="outline-none">
                {field.displayName}
              </SheetTitle>
              <SheetDescription className="font-mono text-xs">{field.key}</SheetDescription>
            </SheetHeader>
            <div className="flex flex-col gap-5 px-4 pb-6">
              <section aria-labelledby="inspector-value" className="space-y-2">
                <h3 id="inspector-value" className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                  Current value
                </h3>
                <div className="text-sm">
                  <MetadataValueView value={entry?.value} dataType={field.dataType} />
                </div>
                {entry && (
                  <div className="flex flex-wrap gap-1.5">
                    <SourceBadge source={entry.source} />
                    <MetadataStatusBadge status={entry.status} />
                    <AuthorityBadge authoritative={entry.authoritative} />
                  </div>
                )}
                {issues.length > 0 && (
                  <ul className="space-y-1 text-sm">
                    {issues.map((issue) => (
                      <li key={`${issue.code}-${issue.message}`} className={issue.severity === 'ERROR' ? 'text-red-700 dark:text-red-300' : 'text-amber-800 dark:text-amber-300'}>
                        {issue.severity === 'ERROR' ? 'Error' : 'Warning'}: {issue.message}
                      </li>
                    ))}
                  </ul>
                )}
              </section>

              {entry && (
                <section aria-labelledby="inspector-provenance" className="space-y-2">
                  <h3 id="inspector-provenance" className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                    Provenance
                  </h3>
                  <KeyValueList
                    items={[
                      { label: 'Source', value: humanize(entry.source) },
                      { label: 'Last updated', value: formatDateTime(entry.updatedAt) },
                      { label: 'Updated by', value: entry.updatedBy.startsWith('ai:') ? agentName(entry.updatedBy) : entry.updatedBy },
                      ...(entry.confidence !== null ? [{ label: 'Confidence', value: <ConfidenceIndicator confidence={entry.confidence} /> }] : []),
                      ...(entry.aiModel ? [{ label: 'Model', value: <span className="font-mono text-xs">{entry.aiModel}</span> }] : []),
                    ]}
                  />
                  {entry.origin && (
                    <div className="space-y-2 rounded-md border border-violet-200 bg-violet-50/50 p-3 text-sm dark:border-violet-900 dark:bg-violet-950/30">
                      <div className="flex flex-wrap items-center gap-1.5">
                        <Pill tone="ai" icon={Sparkles}>
                          From AI suggestion
                        </Pill>
                        <RouteBadge route={entry.origin.route} />
                        {entry.origin.editedByReviewer && <Pill tone="info">Edited by reviewer</Pill>}
                      </div>
                      <KeyValueList
                        items={[
                          { label: 'Agent', value: agentName(entry.origin.agent) },
                          { label: 'Model', value: <span className="font-mono text-xs">{entry.origin.aiModel ?? '—'}</span> },
                          { label: 'Confidence', value: <ConfidenceIndicator confidence={entry.origin.confidence} /> },
                          { label: 'Explanation', value: entry.origin.explanation ?? '—' },
                          {
                            label: 'Evidence',
                            value: entry.origin.evidence.length ? (
                              <ul className="list-disc space-y-0.5 pl-4">
                                {entry.origin.evidence.map((evidence) => (
                                  <li key={evidence}>{evidence}</li>
                                ))}
                              </ul>
                            ) : (
                              '—'
                            ),
                          },
                          {
                            label: 'Approval',
                            value: `${entry.origin.decidedBy?.startsWith('system:') ? 'Applied by approval policy' : `Approved by ${entry.origin.decidedBy ?? '—'}`} · ${formatDateTime(entry.origin.decidedAt)}`,
                          },
                        ]}
                      />
                      {entry.origin.workflowRunId && (
                        <Link className="text-xs text-primary hover:underline" to={`/assets/${assetId}/enrich?run=${entry.origin.workflowRunId}`}>
                          View the enrichment workflow run
                        </Link>
                      )}
                    </div>
                  )}
                </section>
              )}

              {proposal && (
                <section aria-labelledby="inspector-proposal" className="space-y-2 rounded-md border border-amber-200 bg-amber-50/60 p-3 dark:border-amber-900 dark:bg-amber-950/30">
                  <h3 id="inspector-proposal" className="text-sm font-semibold">
                    AI suggestion awaiting review
                  </h3>
                  <div className="text-sm">
                    <MetadataValueView value={proposal.proposedValue} dataType={field.dataType} />
                  </div>
                  <ConfidenceIndicator confidence={proposal.confidence} />
                  <div>
                    <Button asChild size="sm" variant="outline">
                      <Link to={`/assets/${assetId}?tab=suggestions`}>Review suggestion</Link>
                    </Button>
                  </div>
                </section>
              )}

              <section aria-labelledby="inspector-definition" className="space-y-2">
                <h3 id="inspector-definition" className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                  Definition
                </h3>
                <FieldDefinitionDetails field={field} />
              </section>

              <section aria-labelledby="inspector-guidance" className="space-y-2">
                <h3 id="inspector-guidance" className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                  Governance guidance (RAG)
                </h3>
                <KnowledgeHint question={`What does ${field.key} mean?`} />
              </section>

              <Separator />
              <div className="flex flex-wrap gap-2">
                <Button size="sm" onClick={() => onEdit(field)}>
                  <Pencil aria-hidden />
                  Edit
                </Button>
                <Button size="sm" variant="outline" onClick={() => navigate(`/assets/${assetId}?tab=history&field=${field.key}`)}>
                  <History aria-hidden />
                  View history
                </Button>
                <Button size="sm" variant="outline" onClick={() => validate.mutate()} disabled={validate.isPending}>
                  <ShieldCheck aria-hidden />
                  Revalidate
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => enrich.mutate(assetId)}
                  disabled={!field.ai.enrichable || !isAiAvailable(status) || enrich.isPending}
                  title={
                    !field.ai.enrichable
                      ? 'The metadata schema does not allow AI values for this field'
                      : !isAiAvailable(status)
                        ? 'AI service unavailable'
                        : 'Runs the enrichment workflow for this asset'
                  }
                >
                  <Sparkles aria-hidden />
                  Request AI suggestion
                </Button>
              </div>
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  )
}
