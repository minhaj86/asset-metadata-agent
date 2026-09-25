import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { Bot, CalendarClock, CircleAlert, Gauge, Images, ListChecks, Sparkles, XCircle } from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'
import { PageHeader, Section } from '@/components/PageHeader'
import { ErrorState } from '@/components/States'
import { fetchDashboard } from '@/api/dashboard'
import { isTerminal } from '@/api/enrichment'
import { queryKeys } from '@/api/queryKeys'
import { DistributionBars } from '@/features/dashboard/DistributionBars'
import { KpiTile } from '@/features/dashboard/KpiTile'
import { ProposalOutcomesChart } from '@/features/dashboard/ProposalOutcomesChart'
import { RecentWorkflows } from '@/features/dashboard/RecentWorkflows'
import type { DistributionBucket } from '@/types/api'
import { formatDateTime, formatNumber, formatPercent } from '@/utils/format'

const COMPLETENESS_COLORS = ['#059669', '#65a30d', '#d97706', '#dc2626']
const COMPLETENESS_FILTER: Record<number, string> = { 95: '95', 80: '80', 60: '60', 0: '0' }

function completenessLink(bucket: DistributionBucket): string {
  return `/assets?completeness=${COMPLETENESS_FILTER[bucket.min] ?? ''}`
}

export default function DashboardPage() {
  const dashboard = useQuery({
    queryKey: queryKeys.dashboard,
    queryFn: ({ signal }) => fetchDashboard(signal),
    refetchInterval: (query) => (query.state.data?.enrichment.recentRuns.some((run) => !isTerminal(run.status)) ? 3000 : 30_000),
  })

  if (dashboard.isPending) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true" aria-label="Loading dashboard">
        <Skeleton className="h-10 w-72" />
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          {Array.from({ length: 8 }, (_, index) => (
            <Skeleton key={index} className="h-24" />
          ))}
        </div>
        <Skeleton className="h-72" />
      </div>
    )
  }
  if (dashboard.isError) {
    return <ErrorState error={dashboard.error} onRetry={() => void dashboard.refetch()} />
  }
  const { assets, proposals, rights, enrichment } = dashboard.data

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="Dashboard"
        description="Metadata health, review backlog, usage rights and AI workflow activity across the catalog."
        actions={<span className="text-xs text-muted-foreground">Updated {formatDateTime(dashboard.data.generatedAt)}</span>}
      />

      <section aria-label="Key figures" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <KpiTile label="Total assets" value={formatNumber(assets.total)} icon={Images} to="/assets" hint={`${assets.byStatus.READY ?? 0} ready · ${assets.byStatus.DRAFT ?? 0} draft`} />
        <KpiTile
          label="Missing required metadata"
          value={formatNumber(assets.missingRequired)}
          icon={CircleAlert}
          to="/assets?missingRequired=true"
          attention={assets.missingRequired > 0}
          hint="Assets with at least one required field empty"
        />
        <KpiTile
          label="Metadata quality (avg.)"
          value={formatPercent(assets.averageQualityScore)}
          icon={Gauge}
          hint={`Completeness ${formatPercent(assets.averageCompleteness)} · deterministic`}
        />
        <KpiTile label="Pending AI proposals" value={formatNumber(proposals.pending)} icon={Sparkles} to="/approvals" hint={`${proposals.pendingLowConfidence} low-confidence suggestions`} />
        <KpiTile
          label="Pending human approvals"
          value={formatNumber(proposals.pendingHumanReview)}
          icon={ListChecks}
          to="/approvals"
          attention={proposals.pendingHumanReview > 0}
          hint="Routed to review by backend policy"
        />
        <KpiTile
          label="Expiring usage rights"
          value={formatNumber(rights.expiringWithinWarningWindow)}
          icon={CalendarClock}
          to="/assets?rightsStatus=EXPIRING"
          attention={rights.expiringWithinWarningWindow > 0}
          hint={`Within ${rights.warningWindowDays} days`}
        />
        <KpiTile label="AI enrichments today" value={formatNumber(enrichment.runsToday)} icon={Bot} to="/enrichment" hint={enrichment.active ? `${enrichment.active} running now` : 'No active runs'} />
        <KpiTile
          label="Failed enrichment workflows"
          value={formatNumber(enrichment.failedToday)}
          icon={XCircle}
          to="/enrichment"
          attention={enrichment.failedToday > 0}
          hint="Today · AI failures never change metadata"
        />
      </section>

      <div className="grid gap-4 xl:grid-cols-3">
        <Section title="Metadata completeness" description="Share of required and recommended fields populated" className="xl:col-span-1">
          <DistributionBars buckets={dashboard.data.completenessDistribution} total={assets.total} linkFor={completenessLink} colors={COMPLETENESS_COLORS} />
        </Section>
        <Section title="AI proposal outcomes" description="All proposals created by enrichment workflows" className="xl:col-span-2">
          <ProposalOutcomesChart outcomes={proposals.outcomes} />
        </Section>
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        <Section title="AI workflow activity" description="Latest enrichment runs" className="xl:col-span-2" contentClassName="p-0">
          <RecentWorkflows runs={enrichment.recentRuns} />
        </Section>
        <div className="flex flex-col gap-4">
          <Section title="Usage rights" contentClassName="p-0">
            <ul className="divide-y text-sm">
              {[
                { label: 'Expiring in 7 days', value: rights.expiringWithin7Days, to: '/assets?rightsStatus=EXPIRING' },
                { label: `Expiring in ${rights.warningWindowDays} days`, value: rights.expiringWithinWarningWindow, to: '/assets?rightsStatus=EXPIRING' },
                { label: 'Expired', value: rights.expired, to: '/assets?rightsStatus=EXPIRED' },
                { label: 'Missing rights information', value: rights.missing, to: '/assets?rightsStatus=MISSING' },
              ].map((row) => (
                <li key={row.label}>
                  <Link to={row.to} className="flex items-center justify-between px-4 py-2.5 hover:bg-muted/40">
                    <span>{row.label}</span>
                    <span className="font-semibold tabular">{row.value}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </Section>
          <Section title="Quality score distribution">
            <DistributionBars buckets={dashboard.data.qualityDistribution} total={assets.total} colors={COMPLETENESS_COLORS} />
          </Section>
        </div>
      </div>
    </div>
  )
}
