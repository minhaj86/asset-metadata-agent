import { z } from 'zod'
import type { FieldDefinition, MetadataValue } from '@/types/api'

/** Form representation: every input is a string; repeatable fields are string lists. */
export type FormValue = string | string[]

export const MAX_REASON_LENGTH = 1000

export function toFormValue(field: FieldDefinition, value: MetadataValue | undefined): FormValue {
  if (field.repeatable) {
    if (Array.isArray(value)) {
      return value.map(String)
    }
    return value === null || value === undefined || value === '' ? [] : [String(value)]
  }
  if (value === null || value === undefined) {
    return field.dataType === 'BOOLEAN' ? 'false' : ''
  }
  return Array.isArray(value) ? value.map(String).join(', ') : String(value)
}

/** Converts the form value to the JSON value the API expects (string, number, boolean, list). */
export function toApiValue(field: FieldDefinition, value: FormValue): unknown {
  if (Array.isArray(value)) {
    return value.map((item) => item.trim()).filter(Boolean)
  }
  const text = value.trim()
  switch (field.dataType) {
    case 'INTEGER':
    case 'DECIMAL':
      return text === '' ? text : Number(text)
    case 'BOOLEAN':
      return text === 'true'
    default:
      return text
  }
}

/** Splits free-text list input ("a, b, c") into items. */
export function splitList(text: string): string[] {
  return text
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

/**
 * Client-side checks derived from the backend's field definition, for immediate feedback only. The backend
 * validates again and its issues are shown in the form when it rejects a value.
 */
export function fieldFormSchema(field: FieldDefinition) {
  const rules = field.validation
  return z.object({
    reason: z.string().max(MAX_REASON_LENGTH, `At most ${MAX_REASON_LENGTH} characters`),
    value: z.union([z.string(), z.array(z.string())]).superRefine((value, context) => {
      const issue = (message: string) => context.addIssue({ code: 'custom', message })
      const items = Array.isArray(value) ? value.map((item) => item.trim()).filter(Boolean) : [value.trim()].filter(Boolean)
      if (items.length === 0) {
        issue(`${field.displayName} needs a value`)
        return
      }
      if (rules?.maxItems && items.length > rules.maxItems) {
        issue(`At most ${rules.maxItems} values`)
      }
      for (const item of items) {
        if (field.allowedValues.length > 0 && !field.allowedValues.includes(item)) {
          issue(`"${item}" is not an allowed value`)
        }
        if (rules?.minLength && item.length < rules.minLength) {
          issue(`At least ${rules.minLength} characters`)
        }
        if (rules?.maxLength && item.length > rules.maxLength) {
          issue(`At most ${rules.maxLength} characters`)
        }
        if (rules?.pattern && !new RegExp(rules.pattern).test(item)) {
          issue(`Does not match the required format (${rules.pattern})`)
        }
        if (field.dataType === 'INTEGER' && !/^-?\d+$/.test(item)) {
          issue('Must be a whole number')
        }
        if (field.dataType === 'DECIMAL' && Number.isNaN(Number(item))) {
          issue('Must be a number')
        }
        if ((field.dataType === 'INTEGER' || field.dataType === 'DECIMAL') && !Number.isNaN(Number(item))) {
          if (rules?.minValue !== null && rules?.minValue !== undefined && Number(item) < rules.minValue) {
            issue(`Must be at least ${rules.minValue}`)
          }
          if (rules?.maxValue !== null && rules?.maxValue !== undefined && Number(item) > rules.maxValue) {
            issue(`Must be at most ${rules.maxValue}`)
          }
        }
        if (field.dataType === 'DATE' && !/^\d{4}-\d{2}-\d{2}$/.test(item)) {
          issue('Must be a date (YYYY-MM-DD)')
        }
        if (field.dataType === 'URL' && !/^https?:\/\/\S+$/i.test(item)) {
          issue('Must be an absolute http(s) URL')
        }
      }
    }),
  })
}

export type FieldFormValues = z.infer<ReturnType<typeof fieldFormSchema>>
