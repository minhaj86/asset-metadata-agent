import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { ExternalLink } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Field, FieldDescription, FieldError, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { KeyValueList, PageHeader, Section } from '@/components/PageHeader'
import { ErrorState } from '@/components/States'
import { useCurrentUser } from '@/hooks/useCurrentUser'
import { useSystemStatus } from '@/hooks/useSystemStatus'
import type { CapabilityStatus } from '@/types/api'
import { formatConfidence, formatDateTime, humanize } from '@/utils/format'

const identitySchema = z.object({
  userId: z
    .string()
    .trim()
    .min(2, 'At least 2 characters')
    .max(200, 'At most 200 characters')
    .refine((value) => !value.startsWith('ai:') && !value.startsWith('system:'), 'The prefixes ai: and system: are reserved for agents and the platform'),
})

type IdentityValues = z.infer<typeof identitySchema>

function capability(status: CapabilityStatus) {
  return `${humanize(status.state)}${status.detail ? ` — ${status.detail}` : ''}`
}

export default function SettingsPage() {
  const [user, setUser] = useCurrentUser()
  const { data: status, isPending, isError, error, refetch } = useSystemStatus()
  const form = useForm<IdentityValues>({ resolver: zodResolver(identitySchema), defaultValues: { userId: user } })

  const submit = form.handleSubmit(({ userId }) => {
    setUser(userId)
    toast.success('Reviewer identity updated', { description: `Requests are now sent as ${userId}` })
  })

  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="Settings" description="Reviewer identity and the backend configuration this UI works with (read-only; configured through environment variables)." />
      <div className="grid gap-4 xl:grid-cols-2">
        <Section title="Reviewer identity" description="Sent as X-User-Id — a stand-in for an identity provider. Approvals require an identified reviewer.">
          <form onSubmit={submit} noValidate className="flex flex-col gap-3">
            <Field data-invalid={form.formState.errors.userId ? true : undefined}>
              <FieldLabel htmlFor="reviewer-id">User ID</FieldLabel>
              <Input id="reviewer-id" aria-invalid={form.formState.errors.userId ? true : undefined} {...form.register('userId')} />
              <FieldDescription>Every manual change and approval decision is audited under this identity.</FieldDescription>
              <FieldError role="alert" errors={[form.formState.errors.userId]} />
            </Field>
            <Button type="submit" size="sm" className="self-start">
              Save identity
            </Button>
          </form>
        </Section>

        <Section title="System configuration">
          {isPending ? (
            <Skeleton className="h-40" />
          ) : isError ? (
            <ErrorState error={error} onRetry={() => void refetch()} />
          ) : (
            <KeyValueList
              items={[
                { label: 'Environment', value: status.environment },
                { label: 'AI provider', value: status.ai.provider ?? '—' },
                { label: 'Chat model', value: <span className="font-mono text-xs">{status.ai.model ?? '—'}</span> },
                { label: 'Embedding model', value: <span className="font-mono text-xs">{status.ai.embeddingModel ?? status.vectorSearch.embeddingModel ?? '—'}</span> },
                { label: 'AI status', value: capability(status.ai) },
                { label: 'Vector search', value: capability(status.vectorSearch) },
                { label: 'DAM', value: `${status.dam.provider ?? 'DAM'} · ${capability(status.dam)}` },
                { label: 'Auto-approve threshold', value: formatConfidence(status.governance.autoApproveThreshold) },
                { label: 'Human review threshold', value: formatConfidence(status.governance.humanReviewThreshold) },
                { label: 'Metadata schema', value: `Version ${status.governance.schemaVersion}` },
                { label: 'Workflow timeout', value: status.ai.timeoutSeconds ? `${status.ai.timeoutSeconds} s` : '—' },
                { label: 'Concurrent enrichments', value: String(status.maxConcurrentEnrichments) },
                { label: 'Checked', value: formatDateTime(status.checkedAt) },
              ]}
            />
          )}
        </Section>

        <Section title="Developer links">
          <ul className="flex flex-col gap-2 text-sm">
            {[
              { href: '/swagger-ui.html', label: 'OpenAPI / Swagger UI' },
              { href: '/v3/api-docs', label: 'OpenAPI document (JSON)' },
              { href: '/actuator/health', label: 'Health (incl. AI component)' },
              { href: '/actuator/prometheus', label: 'Metrics (Prometheus)' },
            ].map((link) => (
              <li key={link.href}>
                <a href={link.href} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-primary hover:underline">
                  {link.label}
                  <ExternalLink aria-hidden className="size-3" />
                </a>
              </li>
            ))}
          </ul>
        </Section>
      </div>
    </div>
  )
}
