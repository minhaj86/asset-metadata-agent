import type { ComponentType } from 'react'
import {
  Bot,
  FolderTree,
  History,
  Images,
  LayoutDashboard,
  ListChecks,
  Search,
  Settings,
  TableProperties,
} from 'lucide-react'

export interface NavItem {
  to: string
  label: string
  icon: ComponentType<{ className?: string }>
  /** show the pending human approvals count */
  showApprovalsCount?: boolean
}

export const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/assets', label: 'Assets', icon: Images },
  { to: '/search', label: 'Search', icon: Search },
  { to: '/enrichment', label: 'AI Enrichment', icon: Bot },
  { to: '/approvals', label: 'Approvals', icon: ListChecks, showApprovalsCount: true },
  { to: '/taxonomy', label: 'Taxonomy', icon: FolderTree },
  { to: '/metadata-schema', label: 'Metadata Schema', icon: TableProperties },
  { to: '/audit', label: 'Audit', icon: History },
  { to: '/settings', label: 'Settings', icon: Settings },
]
