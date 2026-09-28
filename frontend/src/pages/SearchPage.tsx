import { useMemo, useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import { AlertTriangle, Code2, Search, SearchX } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { FilterSelect } from '@/components/FilterSelect'
import { PageHeader } from '@/components/PageHeader'
import { EmptyState, ErrorState } from '@/components/States'
import { naturalLanguageSearch, structuredSearch } from '@/api/search'
import { useSchema } from '@/features/assets/assetQueries'
import { InterpretedQueryPanel } from '@/features/search/InterpretedQueryPanel'
import { SearchResults } from '@/features/search/SearchResults'
import { ASSET_TYPES, type AssetType, type SearchCriteria, type SearchResult } from '@/types/api'
import { humanize } from '@/utils/format'

const EXAMPLES = [
  "front-facing blue men's shirts",
  'Find front-facing red dresses from summer campaign 2026.',
  'Show product images with missing copyright metadata',
  'Assets whose usage rights expire within 30 days',
  'Find blue shirts similar to asset DA-100001',
]

const FILTER_FIELDS = ['productType', 'color', 'campaign', 'market'] as const

interface Outcome {
  query: string | null
  interpretedByAi: boolean
  notes: string | null
  criteria: SearchCriteria
  result: SearchResult
}

export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [draft, setDraft] = useState(searchParams.get('q') ?? '')
  const [debug, setDebug] = useState(false)
  const schema = useSchema('PRODUCT_IMAGE')
  const committed = searchParams.get('q') ?? ''
  const assetType = (searchParams.get('assetType') as AssetType | null) ?? undefined
  const rightsBefore = searchParams.get('rightsBefore') ?? undefined
  const filters = useMemo(() => {
    const values: Record<string, string> = {}
    FILTER_FIELDS.forEach((field) => {
      const value = searchParams.get(field)
      if (value) {
        values[field] = value
      }
    })
    return values
  }, [searchParams])
  const hasCriteria = committed.trim() !== '' || Object.keys(filters).length > 0 || Boolean(assetType) || Boolean(rightsBefore)

  const search = useQuery({
    queryKey: ['search', committed, filters, assetType, rightsBefore],
    enabled: hasCriteria,
    staleTime: 30_000,
    queryFn: async (): Promise<Outcome> => {
      if (committed.trim()) {
        const response = await naturalLanguageSearch({ query: committed, filters, assetType, rightsExpiryBefore: rightsBefore, limit: 30 })
        return { query: committed, interpretedByAi: response.interpretedByAi, notes: response.interpretationNotes, criteria: response.interpretedCriteria, result: response.search }
      }
      const result = await structuredSearch({ filters, assetType, rightsExpiryBefore: rightsBefore, limit: 50 })
      return { query: null, interpretedByAi: false, notes: null, criteria: result.criteria, result }
    },
  })

  const setParam = (key: string, value: string | undefined) =>
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

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setParam('q', draft.trim() || undefined)
  }
  const fieldLabel = (key: string) => schema.data?.fields.find((field) => field.key === key)?.displayName ?? humanize(key)
  const vocabulary = (key: string) => (schema.data?.fields.find((field) => field.key === key)?.allowedValues ?? []).map((value) => ({ value, label: humanize(value) }))
  const outcome = search.data
  const vectorUnavailable = outcome?.result.degraded ?? false

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="Search"
        description="Natural language is interpreted by the SearchIntentAgent; the validated criteria are executed deterministically as hybrid search (structured filters + pgvector similarity)."
      />
      <form onSubmit={submit} role="search" className="flex flex-col gap-3 rounded-lg border bg-card p-3">
        <Label htmlFor="nl-search" className="sr-only">
          Search assets using natural language
        </Label>
        <div className="flex gap-2">
          <div className="relative flex-1">
            <Search aria-hidden className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              id="nl-search"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder="Search assets using natural language…"
              className="h-10 pl-9 text-base"
            />
          </div>
          <Button type="submit" className="h-10">
            Search
          </Button>
        </div>
        <div className="flex flex-wrap items-center gap-1.5 text-xs">
          <span className="text-muted-foreground">Try:</span>
          {EXAMPLES.map((example) => (
            <button
              key={example}
              type="button"
              onClick={() => {
                setDraft(example)
                setParam('q', example)
              }}
              className="rounded-full border px-2 py-0.5 hover:bg-muted"
            >
              {example}
            </button>
          ))}
        </div>
        <div className="flex flex-wrap items-end gap-3 border-t pt-3">
          <FilterSelect
            label="Asset type"
            value={assetType}
            options={ASSET_TYPES.map((type) => ({ value: type, label: humanize(type) }))}
            onChange={(value) => setParam('assetType', value)}
          />
          <FilterSelect label="Product type" value={filters.productType} options={vocabulary('productType')} onChange={(value) => setParam('productType', value)} />
          <FilterSelect label="Colour" value={filters.color} options={vocabulary('color')} onChange={(value) => setParam('color', value)} />
          <FilterSelect label="Market" value={filters.market} options={vocabulary('market')} onChange={(value) => setParam('market', value)} />
          <div className="flex w-40 flex-col gap-1">
            <Label htmlFor="search-campaign" className="text-xs text-muted-foreground">
              Campaign
            </Label>
            <Input
              id="search-campaign"
              className="h-7"
              defaultValue={filters.campaign}
              placeholder="SUMMER_2026"
              onBlur={(event) => setParam('campaign', event.target.value.trim().toUpperCase() || undefined)}
            />
          </div>
          <div className="flex w-40 flex-col gap-1">
            <Label htmlFor="search-rights" className="text-xs text-muted-foreground">
              Rights expire before
            </Label>
            <Input id="search-rights" type="date" className="h-7" value={rightsBefore ?? ''} onChange={(event) => setParam('rightsBefore', event.target.value || undefined)} />
          </div>
          <div className="ml-auto flex h-7 items-center gap-2">
            <Switch id="search-debug" checked={debug} onCheckedChange={setDebug} />
            <Label htmlFor="search-debug" className="flex items-center gap-1 font-normal">
              <Code2 aria-hidden className="size-3.5" />
              Show interpreted query
            </Label>
          </div>
        </div>
      </form>

      {!hasCriteria ? (
        <EmptyState icon={Search} title="Describe what you are looking for" description="Combine a natural-language query with structured filters, or use filters alone." />
      ) : search.isPending ? (
        <div className="space-y-2" aria-busy="true" aria-label="Searching">
          {Array.from({ length: 4 }, (_, index) => (
            <Skeleton key={index} className="h-24 w-full" />
          ))}
        </div>
      ) : search.isError ? (
        <ErrorState error={search.error} onRetry={() => void search.refetch()} />
      ) : (
        outcome && (
          <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
            <div className="flex flex-col gap-3">
              {vectorUnavailable && (
                <Alert>
                  <AlertTriangle aria-hidden />
                  <AlertTitle>Vector search unavailable</AlertTitle>
                  <AlertDescription>Structured search is still available; results are not ranked by similarity.</AlertDescription>
                </Alert>
              )}
              <p className="text-sm text-muted-foreground" aria-live="polite">
                {outcome.result.count} result(s){outcome.result.semanticApplied ? ' · ranked by semantic similarity' : ''}
              </p>
              {outcome.result.results.length === 0 ? (
                <EmptyState icon={SearchX} title="No matching assets" description="Try fewer filters or a broader description." />
              ) : (
                <SearchResults hits={outcome.result.results} criteria={outcome.criteria} semanticApplied={outcome.result.semanticApplied} fieldLabel={fieldLabel} />
              )}
            </div>
            {debug && (
              <InterpretedQueryPanel
                query={outcome.query ?? '(structured filters only)'}
                criteria={outcome.criteria}
                interpretedByAi={outcome.interpretedByAi}
                notes={outcome.notes}
                warnings={outcome.result.warnings}
                fieldLabel={fieldLabel}
              />
            )}
          </div>
        )
      )}
    </div>
  )
}
