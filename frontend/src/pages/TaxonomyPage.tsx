import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { FolderTree, Images, Info, Search } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { KeyValueList, PageHeader, Section } from '@/components/PageHeader'
import { EmptyState, ErrorState } from '@/components/States'
import { queryKeys } from '@/api/queryKeys'
import { fetchTaxonomyNodes, fetchTaxonomyUsage, searchTaxonomy } from '@/api/taxonomy'
import { TaxonomyBreadcrumb } from '@/features/taxonomy/AssetTaxonomyPanel'
import { TaxonomyTree } from '@/features/taxonomy/TaxonomyTree'
import { ancestorIds, buildTree } from '@/features/taxonomy/treeModel'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'

export default function TaxonomyPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const nodes = useQuery({ queryKey: queryKeys.taxonomyNodes, queryFn: ({ signal }) => fetchTaxonomyNodes(signal), staleTime: 5 * 60_000 })
  const usage = useQuery({ queryKey: queryKeys.taxonomyUsage, queryFn: ({ signal }) => fetchTaxonomyUsage(signal) })
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query)
  const matches = useQuery({
    queryKey: ['taxonomy', 'search', debouncedQuery],
    queryFn: ({ signal }) => searchTaxonomy(debouncedQuery, signal),
    enabled: debouncedQuery.trim().length > 1,
  })

  const selectedPath = searchParams.get('node')
  const selected = nodes.data?.find((node) => node.path === selectedPath) ?? null
  const roots = useMemo(() => buildTree(nodes.data ?? [], usage.data ?? []), [nodes.data, usage.data])
  // User overrides on top of the defaults (roots and the selected node's ancestors are expanded).
  const [opened, setOpened] = useState<Set<string>>(new Set())
  const [closed, setClosed] = useState<Set<string>>(new Set())
  const expanded = useMemo(() => {
    const ids = new Set(opened)
    roots.forEach((root) => ids.add(root.node.id))
    if (selected && nodes.data) {
      ancestorIds(nodes.data, selected.id).forEach((id) => ids.add(id))
    }
    closed.forEach((id) => ids.delete(id))
    return ids
  }, [opened, closed, roots, selected, nodes.data])

  const select = (nodeId: string) => {
    const node = nodes.data?.find((candidate) => candidate.id === nodeId)
    if (node) {
      setSearchParams({ node: node.path }, { replace: true })
    }
  }
  const toggle = (nodeId: string) => {
    const isOpen = expanded.has(nodeId)
    const without = (set: Set<string>) => new Set([...set].filter((id) => id !== nodeId))
    setOpened((previous) => (isOpen ? without(previous) : new Set(previous).add(nodeId)))
    setClosed((previous) => (isOpen ? new Set(previous).add(nodeId) : without(previous)))
  }

  const usageOf = (nodeId: string) => usage.data?.find((entry) => entry.nodeId === nodeId)
  const children = selected ? (nodes.data ?? []).filter((node) => node.parentId === selected.id) : []
  const parent = selected?.parentId ? nodes.data?.find((node) => node.id === selected.parentId) : undefined
  const selectedUsage = selected ? usageOf(selected.id) : undefined

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="Taxonomy"
        description="Hierarchical product taxonomy. The Taxonomy Agent retrieves candidate nodes with a search tool and may only choose existing nodes; it cannot create nodes."
      />
      {nodes.isPending ? (
        <Skeleton className="h-96 w-full" aria-label="Loading taxonomy" />
      ) : nodes.isError ? (
        <ErrorState error={nodes.error} onRetry={() => void nodes.refetch()} />
      ) : (
        <div className="grid gap-4 lg:grid-cols-[minmax(0,420px)_1fr]">
          <Section title="Hierarchy" description="Numbers show assets classified at the node or below" contentClassName="flex flex-col gap-3">
            <div className="flex flex-col gap-1">
              <Label htmlFor="taxonomy-search" className="text-xs text-muted-foreground">
                Find a node (deterministic keyword search)
              </Label>
              <div className="relative">
                <Search aria-hidden className="pointer-events-none absolute top-1/2 left-2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input id="taxonomy-search" className="pl-7" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="e.g. men shirt" />
              </div>
              {matches.data && debouncedQuery.trim().length > 1 && (
                <ul className="mt-1 flex flex-col rounded-md border" aria-label="Matching taxonomy nodes">
                  {matches.data.length === 0 ? (
                    <li className="px-3 py-2 text-sm text-muted-foreground">No matching nodes</li>
                  ) : (
                    matches.data.map((node) => (
                      <li key={node.id}>
                        <button type="button" onClick={() => select(node.id)} className="w-full px-3 py-1.5 text-left text-sm hover:bg-muted">
                          {node.path}
                        </button>
                      </li>
                    ))
                  )}
                </ul>
              )}
            </div>
            <div className="max-h-[60vh] overflow-y-auto">
              <TaxonomyTree roots={roots} expanded={expanded} selectedId={selected?.id ?? null} onToggle={toggle} onSelect={select} />
            </div>
          </Section>

          {selected ? (
            <Section title={selected.name} description={<TaxonomyBreadcrumb path={selected.path} />}>
              <div className="flex flex-col gap-4">
                <KeyValueList
                  items={[
                    { label: 'ID', value: <span className="font-mono text-xs">{selected.id}</span> },
                    { label: 'Name', value: selected.name },
                    { label: 'Path', value: selected.path },
                    {
                      label: 'Parent',
                      value: parent ? (
                        <button type="button" className="text-primary hover:underline" onClick={() => select(parent.id)}>
                          {parent.name}
                        </button>
                      ) : (
                        'Root node'
                      ),
                    },
                    {
                      label: 'Children',
                      value: children.length ? (
                        <span className="flex flex-wrap gap-x-3 gap-y-1">
                          {children.map((child) => (
                            <button key={child.id} type="button" className="text-primary hover:underline" onClick={() => select(child.id)}>
                              {child.name}
                            </button>
                          ))}
                        </span>
                      ) : (
                        'None (leaf node)'
                      ),
                    },
                    { label: 'Synonyms', value: selected.synonyms.length ? selected.synonyms.join(', ') : '—' },
                    { label: 'Assets (this node)', value: <span className="tabular">{selectedUsage?.assetCount ?? 0}</span> },
                    { label: 'Assets (incl. below)', value: <span className="tabular">{selectedUsage?.totalAssetCount ?? 0}</span> },
                  ]}
                />
                <div>
                  <Button asChild size="sm">
                    <Link to={`/assets?taxonomyPath=${encodeURIComponent(selected.path)}`}>
                      <Images aria-hidden />
                      View {selectedUsage?.totalAssetCount ?? 0} asset(s)
                    </Link>
                  </Button>
                </div>
                <p className="flex items-start gap-2 text-xs text-muted-foreground">
                  <Info aria-hidden className="mt-0.5 size-3.5 shrink-0" />
                  Taxonomy changes are governed outside this UI. AI may propose existing nodes only, and every taxonomy proposal
                  requires human approval.
                </p>
              </div>
            </Section>
          ) : (
            <EmptyState icon={FolderTree} title="Select a taxonomy node" description="Choose a node in the hierarchy to see its details and assets." />
          )}
        </div>
      )}
    </div>
  )
}
