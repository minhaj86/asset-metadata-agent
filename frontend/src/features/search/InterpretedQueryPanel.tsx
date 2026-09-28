import { Bot, Code2 } from 'lucide-react'
import { Pill } from '@/components/status/StatusBadges'
import type { SearchCriteria } from '@/types/api'
import { formatDate, humanize } from '@/utils/format'

interface InterpretedQueryPanelProps {
  query: string
  criteria: SearchCriteria
  interpretedByAi: boolean
  notes: string | null
  warnings: string[]
  fieldLabel: (key: string) => string
}

/** Debug view of the SearchIntentAgent: the natural-language query and the validated criteria Spring executed. */
export function InterpretedQueryPanel({ query, criteria, interpretedByAi, notes, warnings, fieldLabel }: InterpretedQueryPanelProps) {
  const rows: [string, string][] = [
    ['Asset type', criteria.assetType ? humanize(criteria.assetType) : '—'],
    ['Semantic query', criteria.semanticQuery ? `"${criteria.semanticQuery}"` : '—'],
    ['Missing fields', criteria.missingFields.length ? criteria.missingFields.map(fieldLabel).join(', ') : '—'],
    ['Rights expire before', criteria.rightsExpiryBefore ? formatDate(criteria.rightsExpiryBefore) : '—'],
    ['Rights expire after', criteria.rightsExpiryAfter ? formatDate(criteria.rightsExpiryAfter) : '—'],
    ['Similar to asset', criteria.similarToAssetId ?? '—'],
  ]
  return (
    <section aria-label="Interpreted query" className="flex flex-col gap-3 rounded-lg border bg-muted/20 p-3 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <Code2 aria-hidden className="size-4" />
        <span className="font-semibold">Interpreted query</span>
        {interpretedByAi ? (
          <Pill tone="ai" icon={Bot}>
            SearchIntentAgent
          </Pill>
        ) : (
          <Pill tone="muted">Fallback: semantic only</Pill>
        )}
      </div>
      <dl className="grid grid-cols-[9rem_1fr] gap-x-3 gap-y-1.5">
        <dt className="text-muted-foreground">User query</dt>
        <dd>“{query}”</dd>
        <dt className="text-muted-foreground">Filters</dt>
        <dd>
          {Object.keys(criteria.filters).length ? (
            <ul className="font-mono text-xs">
              {Object.entries(criteria.filters).map(([field, value]) => (
                <li key={field}>
                  {field}: {value}
                </li>
              ))}
            </ul>
          ) : (
            '—'
          )}
        </dd>
        {rows.map(([label, value]) => (
          <div key={label} className="contents">
            <dt className="text-muted-foreground">{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
        {notes && (
          <>
            <dt className="text-muted-foreground">Agent notes</dt>
            <dd>{notes}</dd>
          </>
        )}
      </dl>
      {warnings.length > 0 && (
        <ul className="list-disc space-y-0.5 pl-5 text-xs text-amber-800 dark:text-amber-300">
          {warnings.map((warning) => (
            <li key={warning}>{warning}</li>
          ))}
        </ul>
      )}
      <p className="text-xs text-muted-foreground">
        The agent only interprets intent. Spring validates fields and vocabularies, computes dates and executes the search
        deterministically.
      </p>
    </section>
  )
}
