import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldLabel } from '@/components/ui/field'
import { Textarea } from '@/components/ui/textarea'
import type { Proposal } from '@/types/api'
import { useRejectProposal } from './useProposalMutations'

const rejectSchema = z.object({
  reason: z.string().trim().min(3, 'Please give a reason (at least 3 characters)').max(1000, 'At most 1000 characters'),
})

type RejectValues = z.infer<typeof rejectSchema>

interface RejectDialogProps {
  proposal: Proposal
  open: boolean
  onOpenChange: (open: boolean) => void
  onDone?: () => void
}

/** Rejections require a reason; it is stored with the decision in the audit trail. */
export function RejectDialog({ proposal, open, onOpenChange, onDone }: RejectDialogProps) {
  const reject = useRejectProposal()
  const form = useForm<RejectValues>({ resolver: zodResolver(rejectSchema), defaultValues: { reason: '' } })

  const submit = form.handleSubmit(({ reason }) =>
    reject.mutate(
      { proposal, reason },
      {
        onSuccess: () => {
          form.reset()
          onOpenChange(false)
          onDone?.()
        },
      },
    ),
  )

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={submit} noValidate className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>Reject AI suggestion for {proposal.fieldInfo.displayName}</DialogTitle>
            <DialogDescription>The current value stays unchanged. Your reason is recorded in the audit history.</DialogDescription>
          </DialogHeader>
          <Controller
            control={form.control}
            name="reason"
            render={({ field, fieldState }) => (
              <Field data-invalid={fieldState.invalid || undefined}>
                <FieldLabel htmlFor={`reject-${proposal.id}`}>Reason</FieldLabel>
                <Textarea id={`reject-${proposal.id}`} rows={3} aria-invalid={fieldState.invalid || undefined} {...field} />
                <FieldDescription>For example: “Taxonomy should be Outerwear, not Shirts.”</FieldDescription>
                <FieldError role="alert" errors={[fieldState.error]} />
              </Field>
            )}
          />
          <DialogFooter>
            <Button type="button" variant="outline" size="sm" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="destructive" size="sm" disabled={reject.isPending}>
              {reject.isPending && <Loader2 aria-hidden className="animate-spin" />}
              Reject proposal
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
