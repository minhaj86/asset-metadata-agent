import { Link } from 'react-router'
import { ArrowRight } from 'lucide-react'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ConfidenceIndicator } from '@/components/ConfidenceIndicator'
import { MetadataValueView } from '@/components/MetadataValueView'
import { SourceBadge } from '@/components/status/StatusBadges'
import type { AuditEvent } from '@/types/api'
import { agentName, formatDateTime, humanize } from '@/utils/format'
import { AuditActionBadge } from './AuditTimeline'

function actor(actorId: string): string {
  if (actorId.startsWith('ai:')) {
    return agentName(actorId)
  }
  return actorId.startsWith('system:') ? `System (${actorId.slice(7)})` : actorId
}

/** Global audit log (append-only, newest first). */
export function AuditTable({ events }: { events: AuditEvent[] }) {
  return (
    <div className="overflow-x-auto rounded-lg border">
      <Table className="text-[13px]">
        <TableHeader className="bg-muted/40">
          <TableRow>
            <TableHead>Time</TableHead>
            <TableHead>Asset</TableHead>
            <TableHead>Field</TableHead>
            <TableHead>Action</TableHead>
            <TableHead>Change</TableHead>
            <TableHead>Source</TableHead>
            <TableHead>Actor</TableHead>
            <TableHead>Approved by</TableHead>
            <TableHead>Confidence</TableHead>
            <TableHead>Reason</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {events.map((event) => (
            <TableRow key={event.id} className="align-top">
              <TableCell className="text-xs whitespace-nowrap">
                <time dateTime={event.timestamp}>{formatDateTime(event.timestamp)}</time>
              </TableCell>
              <TableCell>
                {event.externalAssetId ? (
                  <Link to={`/assets/${event.assetId}?tab=history`} className="font-mono text-xs font-medium text-primary hover:underline">
                    {event.externalAssetId}
                  </Link>
                ) : (
                  <span className="font-mono text-xs text-muted-foreground" title="Asset no longer exists">
                    {event.assetId.slice(0, 8)}
                  </span>
                )}
              </TableCell>
              <TableCell className="font-medium">{humanize(event.field)}</TableCell>
              <TableCell>
                <AuditActionBadge action={event.action} />
              </TableCell>
              <TableCell className="max-w-80 whitespace-normal">
                <span className="flex flex-wrap items-center gap-1.5">
                  <MetadataValueView value={event.previousValue} className="text-muted-foreground" clamp />
                  <ArrowRight aria-label="changed to" className="size-3 shrink-0 text-muted-foreground" />
                  <MetadataValueView value={event.newValue} clamp />
                </span>
              </TableCell>
              <TableCell>{event.source ? <SourceBadge source={event.source} /> : '—'}</TableCell>
              <TableCell className="text-xs">{actor(event.actor)}</TableCell>
              <TableCell className="text-xs">{event.approvedBy ? actor(event.approvedBy) : '—'}</TableCell>
              <TableCell>{event.confidence !== null ? <ConfidenceIndicator confidence={event.confidence} variant="compact" /> : '—'}</TableCell>
              <TableCell className="max-w-64 whitespace-normal text-xs text-muted-foreground">{event.reason ?? '—'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
