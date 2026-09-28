import { ArrowRight } from 'lucide-react'
import { ConfidenceIndicator } from '@/components/ConfidenceIndicator'
import { MetadataValueView } from '@/components/MetadataValueView'
import { Pill, SourceBadge, type Tone } from '@/components/status/StatusBadges'
import type { AuditAction, AuditEvent } from '@/types/api'
import { agentName, formatDate, formatTime } from '@/utils/format'

const ACTIONS: Record<AuditAction, { label: string; tone: Tone }> = {
  VALUE_IMPORTED: { label: 'Imported', tone: 'info' },
  VALUE_SET_MANUALLY: { label: 'Set manually', tone: 'neutral' },
  VALUE_REMOVED: { label: 'Removed', tone: 'warning' },
  VALUE_AUTO_APPLIED: { label: 'Auto-applied by policy', tone: 'ai' },
  PROPOSAL_APPROVED: { label: 'Proposal approved', tone: 'success' },
  PROPOSAL_REJECTED: { label: 'Proposal rejected', tone: 'danger' },
  PROPOSAL_SUPERSEDED: { label: 'Proposal superseded', tone: 'muted' },
}

export function AuditActionBadge({ action }: { action: AuditAction }) {
  return <Pill tone={ACTIONS[action].tone}>{ACTIONS[action].label}</Pill>
}

function actorLabel(actor: string): string {
  if (actor.startsWith('ai:')) {
    return agentName(actor)
  }
  if (actor.startsWith('system:')) {
    return `System (${actor.slice(7)})`
  }
  return actor
}

/** Asset-level history as a timeline grouped by day: what changed, from what, by whom, why and with which confidence. */
export function AuditTimeline({ events, fieldLabel }: { events: AuditEvent[]; fieldLabel: (key: string) => string }) {
  const days = new Map<string, AuditEvent[]>()
  events.forEach((event) => {
    const day = formatDate(event.timestamp)
    days.set(day, [...(days.get(day) ?? []), event])
  })
  return (
    <div className="flex flex-col gap-5">
      {[...days.entries()].map(([day, dayEvents]) => (
        <section key={day} aria-label={day}>
          <h3 className="mb-2 text-xs font-semibold tracking-wide text-muted-foreground uppercase">{day}</h3>
          <ol className="relative flex flex-col gap-3 border-l pl-5">
            {dayEvents.map((event) => (
              <li key={event.id} className="relative">
                <span aria-hidden className="absolute top-1.5 -left-[25px] size-2.5 rounded-full border-2 border-background bg-muted-foreground" />
                <div className="flex flex-col gap-1.5 rounded-md border bg-card p-3 text-sm">
                  <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                    <time dateTime={event.timestamp} className="font-mono text-xs text-muted-foreground">
                      {formatTime(event.timestamp)}
                    </time>
                    <span className="font-medium">{fieldLabel(event.field)}</span>
                    <AuditActionBadge action={event.action} />
                    {event.source && <SourceBadge source={event.source} />}
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <MetadataValueView value={event.previousValue} className="text-muted-foreground" clamp />
                    <ArrowRight aria-label="changed to" className="size-3.5 shrink-0 text-muted-foreground" />
                    <MetadataValueView value={event.newValue} clamp />
                  </div>
                  <dl className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
                    <div className="flex gap-1">
                      <dt>By</dt>
                      <dd className="font-medium text-foreground">{actorLabel(event.actor)}</dd>
                    </div>
                    {event.approvedBy && event.approvedBy !== event.actor && (
                      <div className="flex gap-1">
                        <dt>Approved by</dt>
                        <dd className="font-medium text-foreground">{actorLabel(event.approvedBy)}</dd>
                      </div>
                    )}
                    {event.confidence !== null && (
                      <div className="flex items-center gap-1">
                        <dt>Confidence</dt>
                        <dd>
                          <ConfidenceIndicator confidence={event.confidence} variant="compact" />
                        </dd>
                      </div>
                    )}
                    {event.aiModel && (
                      <div className="flex gap-1">
                        <dt>Model</dt>
                        <dd className="font-mono">{event.aiModel}</dd>
                      </div>
                    )}
                    {event.reason && (
                      <div className="flex gap-1">
                        <dt>Reason</dt>
                        <dd className="text-foreground">{event.reason}</dd>
                      </div>
                    )}
                  </dl>
                </div>
              </li>
            ))}
          </ol>
        </section>
      ))}
    </div>
  )
}
