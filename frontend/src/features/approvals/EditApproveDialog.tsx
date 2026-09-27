import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldLabel } from '@/components/ui/field'
import { Textarea } from '@/components/ui/textarea'
import { validationIssues } from '@/api/errors'
import { useSchema } from '@/features/assets/assetQueries'
import { FieldValueInput } from '@/features/metadata/FieldValueInput'
import { fieldFormSchema, toApiValue, toFormValue, type FieldFormValues } from '@/features/metadata/fieldForm'
import type { FieldDefinition, Proposal } from '@/types/api'
import { useApproveProposal } from './useProposalMutations'

interface EditApproveDialogProps {
  proposal: Proposal
  open: boolean
  onOpenChange: (open: boolean) => void
  onDone?: () => void
}

function fallbackDefinition(proposal: Proposal): FieldDefinition {
  return {
    key: proposal.field,
    displayName: proposal.fieldInfo.displayName,
    dataType: proposal.fieldInfo.dataType ?? 'STRING',
    required: false,
    repeatable: proposal.fieldInfo.repeatable,
    searchable: false,
    semanticSearch: false,
    allowedValues: proposal.fieldInfo.allowedValues,
    validation: null,
    ai: { enrichable: true, autoApply: false, humanApprovalRequired: proposal.policy.requiresHumanApproval },
    description: '',
    group: proposal.fieldInfo.group ?? 'OTHER',
  }
}

/** Reviewer edits the AI value before approving; the result is stored as a human-authored (authoritative) value. */
export function EditApproveDialog({ proposal, open, onOpenChange, onDone }: EditApproveDialogProps) {
  const schema = useSchema(proposal.assetType ?? undefined)
  const definition = schema.data?.fields.find((field) => field.key === proposal.field) ?? fallbackDefinition(proposal)
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        {open && <EditApproveForm key={definition.key + String(schema.isSuccess)} proposal={proposal} definition={definition} onClose={() => onOpenChange(false)} onDone={onDone} />}
      </DialogContent>
    </Dialog>
  )
}

function EditApproveForm({ proposal, definition, onClose, onDone }: { proposal: Proposal; definition: FieldDefinition; onClose: () => void; onDone?: () => void }) {
  const approve = useApproveProposal()
  const form = useForm<FieldFormValues>({
    resolver: zodResolver(fieldFormSchema(definition)),
    defaultValues: { value: toFormValue(definition, proposal.proposedValue), reason: '' },
  })
  const submit = form.handleSubmit((values) =>
    approve.mutate(
      { proposal, value: toApiValue(definition, values.value), comment: values.reason },
      {
        onSuccess: () => {
          onClose()
          onDone?.()
        },
        onError: (error) => {
          const issues = validationIssues(error)
          if (issues.length) {
            form.setError('value', { type: 'server', message: issues.map((issue) => issue.message).join(' ') })
          }
        },
      },
    ),
  )
  const valueId = `approve-edit-${proposal.id}`
  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <DialogHeader>
        <DialogTitle>Edit &amp; approve {proposal.fieldInfo.displayName}</DialogTitle>
        <DialogDescription>
          Adjust the AI proposal before approving. The edited value is stored as your manual, authoritative value; the
          original AI proposal is kept for the audit trail.
        </DialogDescription>
      </DialogHeader>
      <Controller
        control={form.control}
        name="value"
        render={({ field, fieldState }) => (
          <Field data-invalid={fieldState.invalid || undefined}>
            <FieldLabel htmlFor={valueId}>{proposal.fieldInfo.displayName}</FieldLabel>
            <FieldValueInput id={valueId} field={definition} value={field.value} onChange={field.onChange} invalid={fieldState.invalid} />
            {definition.description && <FieldDescription>{definition.description}</FieldDescription>}
            <FieldError role="alert" errors={[fieldState.error]} />
          </Field>
        )}
      />
      <Controller
        control={form.control}
        name="reason"
        render={({ field, fieldState }) => (
          <Field data-invalid={fieldState.invalid || undefined}>
            <FieldLabel htmlFor={`${valueId}-comment`}>Comment (optional)</FieldLabel>
            <Textarea id={`${valueId}-comment`} rows={2} {...field} />
            <FieldError errors={[fieldState.error]} />
          </Field>
        )}
      />
      <DialogFooter>
        <Button type="button" variant="outline" size="sm" onClick={onClose}>
          Cancel
        </Button>
        <Button type="submit" size="sm" disabled={approve.isPending}>
          {approve.isPending && <Loader2 aria-hidden className="animate-spin" />}
          Approve edited value
        </Button>
      </DialogFooter>
    </form>
  )
}
