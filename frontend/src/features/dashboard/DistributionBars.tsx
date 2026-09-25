import { Link } from 'react-router'
import type { DistributionBucket } from '@/types/api'

interface DistributionBarsProps {
  buckets: DistributionBucket[]
  total: number
  /** builds a link to the filtered asset list for a bucket */
  linkFor?: (bucket: DistributionBucket) => string | undefined
  colors: string[]
}

/** Horizontal bars with label, share and count as text (accessible without the colours). */
export function DistributionBars({ buckets, total, linkFor, colors }: DistributionBarsProps) {
  return (
    <ul className="flex flex-col gap-2.5">
      {buckets.map((bucket, index) => {
        const share = total > 0 ? Math.round((bucket.count / total) * 100) : 0
        const link = linkFor?.(bucket)
        const label = <span className="w-20 shrink-0 text-sm tabular">{bucket.label}</span>
        return (
          <li key={bucket.label} className="flex items-center gap-3">
            {link ? (
              <Link to={link} className="w-20 shrink-0 text-sm text-primary tabular hover:underline">
                {bucket.label}
              </Link>
            ) : (
              label
            )}
            <div aria-hidden className="h-3 flex-1 overflow-hidden rounded-sm bg-muted">
              <div className="h-full rounded-sm" style={{ width: `${share}%`, backgroundColor: colors[index % colors.length] }} />
            </div>
            <span className="w-24 shrink-0 text-right text-xs text-muted-foreground tabular">
              {share}% · {bucket.count} asset{bucket.count === 1 ? '' : 's'}
            </span>
          </li>
        )
      })}
    </ul>
  )
}
