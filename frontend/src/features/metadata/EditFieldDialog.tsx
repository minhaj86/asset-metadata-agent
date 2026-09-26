import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Info, Loader2, Trash2 } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldLabel } from '@/components/ui/field'
import { Textarea } from '@/components/ui/textarea'
import { validationIssues } from '@/api/errors'
import { MetadataValueView } from '@/components/MetadataValueView'
import type { FieldDefinition, MetadataEntry } from '@/types/api'
import { FieldValueInput } from './FieldValueInput'
import { fieldFormSchema, toApiValue, toFormValue, type FieldFormValues } from './fieldForm'
import { useRemoveMetadataValue, useSetMetadataValue } from './useMetadataMutations'

interface EditFieldDialogProps {
  assetId: string
  field: FieldDefinition
  entry: MetadataEntry | undefined
  open: boolean
  onOpenChange: (open: boolean) => void
}

/**
 * Manual metadata edit. The value is authored by a person (source MANUAL, authoritative) and the change is audited.
 * Client checks give fast feedback; backend validation issues are shown on the field when it rejects a value.
 */
export function EditFieldDialog({ assetId, field, entry, open, onOpenChange }: EditFieldDialogProps) {
  const save = useSetMetadataValue(assetId)
  const remove = useRemoveMetadataValue(assetId)
  const [confirmRemove, setConfirmRemove] = useState(false)
  const form = useForm<FieldFormValues>({
    resolver: zodResolver(fieldFormSchema(field)),
    defaultValues: { value: toFormValue(field, entry?.value), reason: '' },
  })

  const close = () => {
    setConfirmRemove(false)
    form.reset({ value: toFormValue(field, entry?.value), reason: '' })
    onOpenChange(false)
  }

  const submit = form.handleSubmit((values) =>
    save.mutate(
      { field: field.key, value: toApiValue(field, values.value), reason: values.reason },
      {
        onSuccess: close,
        onError: (error) => {
          const issues = validationIssues(error)
          form.setError('value', {
            type: 'server',
            message: issues.length ? issues.map((issue) => issue.message).join(' ') : undefined,
          })
        },
      },
    ),
  )

  const valueId = `edit-${field.key}`
  const errorId = `${valueId}-error`

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? onOpenChange(true) : close())}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={submit} noValidate className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>Edit {field.displayName}</DialogTitle>
            <DialogDescription>
              Manual values are authoritative: AI never overwrites them. The change is validated against the metadata
              schema and recorded in the audit history.
            </DialogDescription>
          </DialogHeader>

          {entry && (
            <div className="rounded-md border bg-muted/40 px-3 py-2 text-sm">
              <span className="text-xs text-muted-foreground">Current value</span>
              <div>
                <MetadataValueView value={entry.value} dataType={field.dataType} />
              </div>
            </div>
          )}

          {field.ai.humanApprovalRequired && (
            <Alert>
              <Info aria-hidden />
              <AlertDescription>
                Governed field: AI suggestions for it always require human approval. Your manual change is audited with your
                identity.
              </AlertDescription>
            </Alert>
          )}

          <Controller
            control={form.control}
            name="value"
            render={({ field: control, fieldState }) => (
              <Field data-invalid={fieldState.invalid || undefined}>
                <FieldLabel htmlFor={valueId}>{field.displayName}</FieldLabel>
                <FieldValueInput
                  id={valueId}
                  field={field}
                  value={control.value}
                  onChange={control.onChange}
                  invalid={fieldState.invalid}
                  describedBy={fieldState.error ? errorId : undefined}
                />
                {field.description && <FieldDescription>{field.description}</FieldDescription>}
                <FieldError id={errorId} role="alert" errors={[fieldState.error]} />
              </Field>
            )}
          />

          <Controller
            control={form.control}
            name="reason"
            render={({ field: control, fieldState }) => (
              <Field data-invalid={fieldState.invalid || undefined}>
                <FieldLabel htmlFor={`${valueId}-reason`}>Reason (optional, stored in the audit trail)</FieldLabel>
                <Textarea id={`${valueId}-reason`} rows={2} {...control} />
                <FieldError errors={[fieldState.error]} />
              </Field>
            )}
          />

          <DialogFooter className="gap-2 sm:justify-between">
            {entry ? (
              confirmRemove ? (
                <Button
                  type="button"
                  variant="destructive"
                  size="sm"
                  disabled={remove.isPending}
                  onClick={() => remove.mutate({ field: field.key, reason: form.getValues('reason') }, { onSuccess: close })}
                >
                  <Trash2 aria-hidden />
                  Confirm removal
                </Button>
              ) : (
                <Button type="button" variant="ghost" size="sm" onClick={() => setConfirmRemove(true)}>
                  <Trash2 aria-hidden />
                  Remove value
                </Button>
              )
            ) : (
              <span />
            )}
            <div className="flex gap-2">
              <Button type="button" variant="outline" size="sm" onClick={close}>
                Cancel
              </Button>
              <Button type="submit" size="sm" disabled={save.isPending}>
                {save.isPending && <Loader2 aria-hidden className="animate-spin" />}
                Save
              </Button>
            </div>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
