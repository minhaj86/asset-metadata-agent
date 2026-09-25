import type { ComponentType, ReactNode } from 'react'
import { Link } from 'react-router'
import { cn } from '@/lib/utils'

interface KpiTileProps {
  label: string
  value: ReactNode
  hint?: ReactNode
  icon: ComponentType<{ className?: string }>
  to?: string
  /** emphasis when the value needs attention */
  attention?: boolean
}

export function KpiTile({ label, value, hint, icon: Icon, to, attention }: KpiTileProps) {
  const body = (
    <>
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-medium text-muted-foreground">{label}</span>
        <Icon aria-hidden className={cn('size-4 text-muted-foreground', attention && 'text-amber-600')} />
      </div>
      <span className={cn('text-2xl font-semibold tabular', attention && 'text-amber-800 dark:text-amber-300')}>{value}</span>
      {hint && <span className="text-xs text-muted-foreground">{hint}</span>}
    </>
  )
  const className = 'flex flex-col gap-1 rounded-lg border bg-card px-4 py-3'
  return to ? (
    <Link to={to} className={cn(className, 'outline-none transition-colors hover:bg-muted/40 focus-visible:ring-2 focus-visible:ring-ring')}>
      {body}
    </Link>
  ) : (
    <div className={className}>{body}</div>
  )
}
