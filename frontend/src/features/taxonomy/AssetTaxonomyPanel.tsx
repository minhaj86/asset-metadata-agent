import { Link } from 'react-router'
import { ChevronRight, FolderTree, Info } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Section } from '@/components/PageHeader'
import { AuthorityBadge, SourceBadge } from '@/components/status/StatusBadges'
import { ProposalReviewCard } from '@/features/approvals/ProposalReviewCard'
import type { MetadataEntry, Proposal } from '@/types/api'
import { formatValue } from '@/utils/format'

interface AssetTaxonomyPanelProps {
  entry: MetadataEntry | undefined
  proposal: Proposal | undefined
}

export function TaxonomyBreadcrumb({ path }: { path: string }) {
  const segments = path.split(' > ')
  return (
    <ol className="flex flex-wrap items-center gap-1 text-sm" aria-label="Taxonomy path">
      {segments.map((segment, index) => (
        <li key={`${segment}-${index}`} className="flex items-center gap-1">
          {index > 0 && <ChevronRight aria-hidden className="size-3.5 text-muted-foreground" />}
          <span className={index === segments.length - 1 ? 'font-medium' : 'text-muted-foreground'}>{segment}</span>
        </li>
      ))}
    </ol>
  )
}

/** Current classification with provenance and any open taxonomy proposal (always human-approved). */
export function AssetTaxonomyPanel({ entry, proposal }: AssetTaxonomyPanelProps) {
  const path = entry ? formatValue(entry.value) : null
  return (
    <div className="flex flex-col gap-4">
      <Section title="Classification" description="Taxonomy node the asset is classified under">
        {path ? (
          <div className="flex flex-col gap-3">
            <TaxonomyBreadcrumb path={path} />
            {entry && (
              <span className="flex flex-wrap gap-1.5">
                <SourceBadge source={entry.source} />
                <AuthorityBadge authoritative={entry.authoritative} />
              </span>
            )}
            <div className="flex flex-wrap gap-2">
              <Button asChild size="sm" variant="outline">
                <Link to={`/taxonomy?node=${encodeURIComponent(path)}`}>
                  <FolderTree aria-hidden />
                  Open in taxonomy browser
                </Link>
              </Button>
              <Button asChild size="sm" variant="outline">
                <Link to={`/assets?taxonomyPath=${encodeURIComponent(path)}`}>Assets in this node</Link>
              </Button>
            </div>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">Not classified yet.</p>
        )}
      </Section>
      {proposal && (
        <section aria-label="Taxonomy proposal" className="flex flex-col gap-2">
          <h2 className="text-sm font-semibold">Proposed classification</h2>
          <ProposalReviewCard proposal={proposal} />
        </section>
      )}
      <p className="flex items-start gap-2 text-xs text-muted-foreground">
        <Info aria-hidden className="mt-0.5 size-3.5 shrink-0" />
        The Taxonomy Agent may only choose existing nodes it retrieved with the taxonomy search tool; invented nodes are
        rejected by deterministic validation. Taxonomy changes always require human approval.
      </p>
    </div>
  )
}
