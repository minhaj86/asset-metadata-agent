import { KeyValueList } from '@/components/PageHeader'
import type { FieldDefinition } from '@/types/api'

function yesNo(value: boolean) {
  return value ? 'Yes' : 'No'
}

/** Plain-language description of the backend's AI governance for a field. */
function aiGovernance(field: FieldDefinition): string {
  if (!field.ai.enrichable) {
    return 'AI may not propose values'
  }
  if (field.ai.humanApprovalRequired) {
    return 'AI may propose; human approval always required'
  }
  return field.ai.autoApply ? 'AI may propose; auto-applied above the confidence threshold' : 'AI may propose; a reviewer decides'
}

/** Definition of a configurable metadata field as delivered by the schema API. */
export function FieldDefinitionDetails({ field }: { field: FieldDefinition }) {
  const rules = field.validation
  return (
    <div className="flex flex-col gap-2">
      {field.description && <p className="text-sm">{field.description}</p>}
      <KeyValueList
        items={[
          { label: 'Data type', value: <span className="font-mono text-xs">{field.dataType}</span> },
          { label: 'Group', value: field.group },
          { label: 'Required', value: yesNo(field.required) },
          { label: 'Repeatable', value: yesNo(field.repeatable) },
          { label: 'Allowed values', value: field.allowedValues.length ? field.allowedValues.join(', ') : 'Free value' },
          ...(rules?.pattern ? [{ label: 'Pattern', value: <code className="text-xs">{rules.pattern}</code> }] : []),
          ...(rules?.minLength || rules?.maxLength
            ? [{ label: 'Length', value: `${rules.minLength ?? 0}–${rules.maxLength ?? '∞'} characters` }]
            : []),
          ...(rules?.minValue !== null && rules?.minValue !== undefined ? [{ label: 'Minimum', value: String(rules.minValue) }] : []),
          ...(rules?.maxValue !== null && rules?.maxValue !== undefined ? [{ label: 'Maximum', value: String(rules.maxValue) }] : []),
          ...(rules?.maxItems ? [{ label: 'Max items', value: String(rules.maxItems) }] : []),
          { label: 'Searchable', value: yesNo(field.searchable) },
          { label: 'Semantic search', value: yesNo(field.semanticSearch) },
          { label: 'AI governance', value: aiGovernance(field) },
        ]}
      />
    </div>
  )
}
