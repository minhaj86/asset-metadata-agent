import { useQuery } from '@tanstack/react-query'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { queryKeys } from '@/api/queryKeys'
import { fetchTaxonomyNodes } from '@/api/taxonomy'
import type { FieldDefinition } from '@/types/api'
import { humanize } from '@/utils/format'
import { splitList, type FormValue } from './fieldForm'

interface FieldValueInputProps {
  id: string
  field: FieldDefinition
  value: FormValue
  onChange: (value: FormValue) => void
  invalid?: boolean
  describedBy?: string
}

/** Input matching the field definition: vocabulary, taxonomy, boolean, date, number, URL, text or list. */
export function FieldValueInput({ id, field, value, onChange, invalid, describedBy }: FieldValueInputProps) {
  const taxonomy = useQuery({
    queryKey: queryKeys.taxonomyNodes,
    queryFn: ({ signal }) => fetchTaxonomyNodes(signal),
    enabled: field.dataType === 'TAXONOMY_PATH',
    staleTime: 5 * 60_000,
  })
  const common = { id, 'aria-invalid': invalid || undefined, 'aria-describedby': describedBy }

  if (field.allowedValues.length > 0 && field.repeatable) {
    const selected = Array.isArray(value) ? value : []
    return (
      <div {...common} role="group" className="grid grid-cols-2 gap-2 rounded-md border p-2 sm:grid-cols-3">
        {field.allowedValues.map((option) => (
          <label key={option} className="flex items-center gap-2 text-sm">
            <Checkbox
              checked={selected.includes(option)}
              onCheckedChange={(checked) =>
                onChange(checked === true ? [...selected, option] : selected.filter((item) => item !== option))
              }
            />
            {humanize(option)}
          </label>
        ))}
      </div>
    )
  }
  if (field.allowedValues.length > 0 || field.dataType === 'TAXONOMY_PATH') {
    const options =
      field.dataType === 'TAXONOMY_PATH'
        ? (taxonomy.data ?? []).map((node) => ({ value: node.path, label: node.path }))
        : field.allowedValues.map((option) => ({ value: option, label: humanize(option) }))
    return (
      <Select value={typeof value === 'string' ? value : ''} onValueChange={onChange}>
        <SelectTrigger {...common} className="w-full">
          <SelectValue placeholder={field.dataType === 'TAXONOMY_PATH' ? 'Select an existing taxonomy node' : 'Select a value'} />
        </SelectTrigger>
        <SelectContent>
          {options.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    )
  }
  if (field.repeatable) {
    const items = Array.isArray(value) ? value : []
    return (
      <Input
        {...common}
        defaultValue={items.join(', ')}
        onChange={(event) => onChange(splitList(event.target.value))}
        placeholder="Comma-separated values"
      />
    )
  }
  const text = typeof value === 'string' ? value : ''
  switch (field.dataType) {
    case 'BOOLEAN':
      return (
        <div className="flex items-center gap-2">
          <Switch {...common} checked={text === 'true'} onCheckedChange={(checked) => onChange(checked ? 'true' : 'false')} />
          <Label htmlFor={id} className="font-normal">
            {text === 'true' ? 'Yes' : 'No'}
          </Label>
        </div>
      )
    case 'DATE':
      return <Input {...common} type="date" value={text} onChange={(event) => onChange(event.target.value)} />
    case 'INTEGER':
    case 'DECIMAL':
      return (
        <Input
          {...common}
          type="number"
          step={field.dataType === 'INTEGER' ? 1 : 'any'}
          value={text}
          onChange={(event) => onChange(event.target.value)}
        />
      )
    case 'TEXT':
      return <Textarea {...common} rows={4} value={text} onChange={(event) => onChange(event.target.value)} />
    case 'URL':
      return <Input {...common} type="url" value={text} onChange={(event) => onChange(event.target.value)} placeholder="https://" />
    default:
      return <Input {...common} value={text} onChange={(event) => onChange(event.target.value)} />
  }
}
