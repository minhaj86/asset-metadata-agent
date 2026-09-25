import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatNumber } from '@/utils/format'

interface PaginationProps {
  page: number
  totalPages: number
  totalItems: number
  pageSize: number
  onPageChange: (page: number) => void
  label?: string
}

/** Zero-based server-side pagination. */
export function Pagination({ page, totalPages, totalItems, pageSize, onPageChange, label = 'items' }: PaginationProps) {
  const first = totalItems === 0 ? 0 : page * pageSize + 1
  const last = Math.min(totalItems, (page + 1) * pageSize)
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between gap-3 text-xs text-muted-foreground">
      <span className="tabular">
        {formatNumber(first)}–{formatNumber(last)} of {formatNumber(totalItems)} {label}
      </span>
      <div className="flex items-center gap-1">
        <Button variant="outline" size="sm" onClick={() => onPageChange(page - 1)} disabled={page <= 0}>
          <ChevronLeft aria-hidden />
          Previous
        </Button>
        <span className="px-2 tabular">
          Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
        </span>
        <Button variant="outline" size="sm" onClick={() => onPageChange(page + 1)} disabled={page + 1 >= totalPages}>
          Next
          <ChevronRight aria-hidden />
        </Button>
      </div>
    </nav>
  )
}
