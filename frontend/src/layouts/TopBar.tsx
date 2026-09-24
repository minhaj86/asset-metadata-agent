import { Link } from 'react-router'
import { CircleUser, Menu, ScanSearch } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useCurrentUser } from '@/hooks/useCurrentUser'
import { useSystemStatus } from '@/hooks/useSystemStatus'
import { cn } from '@/lib/utils'
import type { CapabilityStatus } from '@/types/api'

const STATE_LABEL: Record<CapabilityStatus['state'], string> = {
  AVAILABLE: 'Available',
  UNAVAILABLE: 'Unavailable',
  DISABLED: 'Disabled',
}

const STATE_DOT: Record<CapabilityStatus['state'], string> = {
  AVAILABLE: 'bg-emerald-500',
  UNAVAILABLE: 'bg-red-500',
  DISABLED: 'bg-muted-foreground',
}

function CapabilityIndicator({ name, capability }: { name: string; capability: CapabilityStatus | undefined }) {
  const state = capability?.state
  const label = state ? STATE_LABEL[state] : 'Checking'
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span
          tabIndex={0}
          className="inline-flex items-center gap-1.5 rounded-md px-1.5 py-1 text-xs outline-none focus-visible:ring-2 focus-visible:ring-ring"
          aria-label={`${name}: ${label}${capability?.detail ? `. ${capability.detail}` : ''}`}
        >
          <span aria-hidden className={cn('size-2 rounded-full', state ? STATE_DOT[state] : 'bg-muted-foreground/50')} />
          <span className="font-medium">{name}</span>
          <span className={cn('text-muted-foreground', state === 'UNAVAILABLE' && 'font-medium text-red-700 dark:text-red-300')}>
            {label}
          </span>
        </span>
      </TooltipTrigger>
      <TooltipContent className="max-w-72">
        {capability?.detail ?? `${name} ${label.toLowerCase()}`}
        {capability?.embeddingModel && <div>Embedding model: {capability.embeddingModel}</div>}
      </TooltipContent>
    </Tooltip>
  )
}

export function TopBar({ onOpenNavigation }: { onOpenNavigation: () => void }) {
  const { data: status } = useSystemStatus()
  const [user] = useCurrentUser()
  const model = status?.ai.provider && status.ai.model ? `${status.ai.provider} · ${status.ai.model}` : null

  return (
    <header className="flex h-12 shrink-0 items-center gap-3 border-b bg-background px-3 lg:px-4">
      <Button variant="ghost" size="icon-sm" className="lg:hidden" onClick={onOpenNavigation} aria-label="Open navigation">
        <Menu aria-hidden />
      </Button>
      <Link to="/" className="flex items-center gap-2 rounded-md font-semibold outline-none focus-visible:ring-2 focus-visible:ring-ring">
        <ScanSearch aria-hidden className="size-5 text-violet-600" />
        <span className="whitespace-nowrap">AI Metadata DAM</span>
      </Link>
      {status && (
        <span className="hidden rounded border px-1.5 py-0.5 text-[11px] font-medium tracking-wide text-muted-foreground uppercase sm:inline">
          {status.environment}
        </span>
      )}
      <div className="ml-auto flex items-center gap-1">
        {model && (
          <span className="hidden max-w-56 truncate text-xs text-muted-foreground xl:inline" title="Configured AI provider and model">
            {model}
          </span>
        )}
        <div className="hidden items-center md:flex" aria-label="Capability status">
          <CapabilityIndicator name="AI" capability={status?.ai} />
          <CapabilityIndicator name="Vector search" capability={status?.vectorSearch} />
          <CapabilityIndicator name="DAM" capability={status?.dam} />
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="sm" aria-label={`Signed in as ${user}`}>
              <CircleUser aria-hidden />
              <span className="hidden sm:inline">{user}</span>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuLabel className="text-xs font-normal text-muted-foreground">
              Reviewer identity (X-User-Id)
              <div className="font-medium text-foreground">{user}</div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem asChild>
              <Link to="/settings">Change identity…</Link>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}
