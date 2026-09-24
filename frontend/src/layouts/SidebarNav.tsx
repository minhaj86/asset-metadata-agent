import { useQuery } from '@tanstack/react-query'
import { NavLink } from 'react-router'
import { fetchDashboard } from '@/api/dashboard'
import { queryKeys } from '@/api/queryKeys'
import { cn } from '@/lib/utils'
import { NAV_ITEMS } from './navigation'

export function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const { data: dashboard } = useQuery({
    queryKey: queryKeys.dashboard,
    queryFn: ({ signal }) => fetchDashboard(signal),
    refetchInterval: 30_000,
  })
  const pendingApprovals = dashboard?.proposals.pendingHumanReview ?? 0

  return (
    <nav aria-label="Main" className="flex flex-col gap-0.5 p-2">
      {NAV_ITEMS.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.to === '/'}
          onClick={onNavigate}
          className={({ isActive }) =>
            cn(
              'flex h-8 items-center gap-2.5 rounded-md px-2.5 text-sm text-sidebar-foreground/80 outline-none transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground focus-visible:ring-2 focus-visible:ring-sidebar-ring',
              isActive && 'bg-sidebar-accent font-medium text-sidebar-accent-foreground',
            )
          }
        >
          <item.icon aria-hidden className="size-4 shrink-0" />
          <span className="truncate">{item.label}</span>
          {item.showApprovalsCount && pendingApprovals > 0 && (
            <span
              className="ml-auto rounded-full bg-amber-100 px-1.5 text-[11px] font-semibold text-amber-900 tabular dark:bg-amber-900 dark:text-amber-100"
              aria-label={`${pendingApprovals} pending human approvals`}
            >
              {pendingApprovals}
            </span>
          )}
        </NavLink>
      ))}
    </nav>
  )
}
