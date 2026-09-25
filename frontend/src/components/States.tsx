import type { ComponentType, ReactNode } from 'react'
import { AlertTriangle, BotOff, RefreshCw, Unplug } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { errorMessage, errorTitle, isAiUnavailable, isNetworkError } from '@/api/errors'
import { ApiError } from '@/api/client'
import { cn } from '@/lib/utils'

interface EmptyStateProps {
  icon?: ComponentType<{ className?: string }>
  title: string
  description?: ReactNode
  action?: ReactNode
  className?: string
}

export function EmptyState({ icon: Icon, title, description, action, className }: EmptyStateProps) {
  return (
    <div className={cn('flex flex-col items-center justify-center gap-2 px-6 py-10 text-center', className)}>
      {Icon && <Icon aria-hidden className="size-8 text-muted-foreground" />}
      <p className="text-sm font-medium">{title}</p>
      {description && <p className="max-w-md text-sm text-muted-foreground">{description}</p>}
      {action && <div className="pt-2">{action}</div>}
    </div>
  )
}

interface ErrorStateProps {
  error: unknown
  onRetry?: () => void
  /** extra guidance shown for AI outages, e.g. "The asset can still be edited manually." */
  aiHint?: string
  className?: string
}

/** User-facing error for failed requests: friendly title, backend message and correlation id; never stack traces. */
export function ErrorState({ error, onRetry, aiHint, className }: ErrorStateProps) {
  const Icon = isAiUnavailable(error) ? BotOff : isNetworkError(error) ? Unplug : AlertTriangle
  const correlationId = error instanceof ApiError ? error.problem.correlationId : undefined
  return (
    <Alert variant={isAiUnavailable(error) ? 'default' : 'destructive'} className={className}>
      <Icon aria-hidden />
      <AlertTitle>{errorTitle(error)}</AlertTitle>
      <AlertDescription>
        <p>{errorMessage(error)}</p>
        {isAiUnavailable(error) && aiHint && <p className="font-medium">{aiHint}</p>}
        {correlationId && <p className="text-xs text-muted-foreground">Reference: {correlationId}</p>}
        {onRetry && (
          <Button variant="outline" size="sm" className="mt-2" onClick={onRetry}>
            <RefreshCw aria-hidden />
            Retry
          </Button>
        )}
      </AlertDescription>
    </Alert>
  )
}
