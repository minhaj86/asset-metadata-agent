import { useId, type ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface PageHeaderProps {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
  /** rendered above the title, e.g. a back link */
  eyebrow?: ReactNode
  className?: string
}

export function PageHeader({ title, description, actions, eyebrow, className }: PageHeaderProps) {
  return (
    <header className={cn('flex flex-col gap-3 border-b pb-4 sm:flex-row sm:items-end sm:justify-between', className)}>
      <div className="min-w-0 space-y-1">
        {eyebrow}
        <h1 className="truncate text-xl font-semibold tracking-tight">{title}</h1>
        {description && <div className="max-w-3xl text-sm text-muted-foreground">{description}</div>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </header>
  )
}

interface SectionProps {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
  children: ReactNode
  className?: string
  contentClassName?: string
  id?: string
}

/** Bordered content section with a compact header (dense enterprise layout, no oversized cards). */
export function Section({ title, description, actions, children, className, contentClassName, id }: SectionProps) {
  const generatedId = useId()
  const headingId = id ? `${id}-heading` : generatedId
  return (
    <section aria-labelledby={headingId} className={cn('rounded-lg border bg-card', className)}>
      <div className="flex items-start justify-between gap-3 border-b px-4 py-2.5">
        <div className="min-w-0">
          <h2 id={headingId} className="text-sm font-semibold">
            {title}
          </h2>
          {description && <div className="text-xs text-muted-foreground">{description}</div>}
        </div>
        {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
      </div>
      <div className={cn('p-4', contentClassName)}>{children}</div>
    </section>
  )
}

export function KeyValueList({ items, className }: { items: { label: ReactNode; value: ReactNode }[]; className?: string }) {
  return (
    <dl className={cn('grid grid-cols-[minmax(7rem,auto)_1fr] gap-x-4 gap-y-2 text-sm', className)}>
      {items.map((item, index) => (
        <div key={index} className="contents">
          <dt className="text-muted-foreground">{item.label}</dt>
          <dd className="min-w-0 break-words">{item.value}</dd>
        </div>
      ))}
    </dl>
  )
}
