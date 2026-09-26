import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { Check, Minus } from 'lucide-react'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { FilterSelect } from '@/components/FilterSelect'
import { PageHeader, Section } from '@/components/PageHeader'
import { ErrorState } from '@/components/States'
import { useSchema } from '@/features/assets/assetQueries'
import { FieldDefinitionDetails } from '@/features/metadata/FieldDefinitionDetails'
import { ASSET_TYPES, type AssetType, type FieldDefinition } from '@/types/api'
import { humanize } from '@/utils/format'

function Flag({ value, label }: { value: boolean; label: string }) {
  return value ? (
    <span className="inline-flex items-center gap-1 text-emerald-700 dark:text-emerald-300">
      <Check aria-hidden className="size-3.5" />
      Yes<span className="sr-only"> — {label}</span>
    </span>
  ) : (
    <span className="inline-flex items-center gap-1 text-muted-foreground">
      <Minus aria-hidden className="size-3.5" />
      No<span className="sr-only"> — {label}</span>
    </span>
  )
}

export default function MetadataSchemaPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const assetType = (searchParams.get('type') as AssetType | null) ?? 'PRODUCT_IMAGE'
  const schema = useSchema(assetType)
  const [selected, setSelected] = useState<FieldDefinition | null>(null)

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="Metadata Schema"
        description="Configurable field definitions (YAML, synchronised into the database). Validation and AI governance are enforced by the backend from this schema."
        actions={
          <FilterSelect
            label="Asset type"
            value={assetType}
            allowAny={false}
            options={ASSET_TYPES.map((type) => ({ value: type, label: humanize(type) }))}
            onChange={(value) => setSearchParams(value ? { type: value } : {}, { replace: true })}
            className="w-52"
          />
        }
      />
      {schema.isPending ? (
        <Skeleton className="h-96 w-full" aria-label="Loading schema" />
      ) : schema.isError ? (
        <ErrorState error={schema.error} onRetry={() => void schema.refetch()} />
      ) : (
        <>
          <p className="text-sm text-muted-foreground">
            Schema version {schema.data.version} · {schema.data.fields.length} fields · {schema.data.requiredFields.length} required and{' '}
            {schema.data.recommendedFields.length} recommended for {humanize(assetType).toLowerCase()}
          </p>
          {schema.data.groups.map((group) => {
            const fields = schema.data.fields.filter((field) => field.group === group.key)
            if (fields.length === 0) {
              return null
            }
            return (
              <Section key={group.key} id={`schema-${group.key}`} title={group.displayName} contentClassName="p-0">
                <div className="overflow-x-auto">
                  <Table className="text-[13px]">
                    <TableHeader className="bg-muted/30">
                      <TableRow>
                        <TableHead>Field</TableHead>
                        <TableHead>Type</TableHead>
                        <TableHead>Required</TableHead>
                        <TableHead>Repeatable</TableHead>
                        <TableHead>AI enrichable</TableHead>
                        <TableHead>Auto update</TableHead>
                        <TableHead>Approval required</TableHead>
                        <TableHead>Searchable</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {fields.map((field) => (
                        <TableRow key={field.key}>
                          <TableCell>
                            <button type="button" onClick={() => setSelected(field)} className="flex flex-col items-start rounded-sm text-left outline-none hover:underline focus-visible:ring-2 focus-visible:ring-ring">
                              <span className="font-medium">{field.displayName}</span>
                              <span className="font-mono text-[11px] text-muted-foreground">{field.key}</span>
                            </button>
                          </TableCell>
                          <TableCell className="font-mono text-xs">{field.dataType}</TableCell>
                          <TableCell>
                            <Flag value={field.required} label="required" />
                          </TableCell>
                          <TableCell>
                            <Flag value={field.repeatable} label="repeatable" />
                          </TableCell>
                          <TableCell>
                            <Flag value={field.ai.enrichable} label="AI enrichable" />
                          </TableCell>
                          <TableCell>
                            <Flag value={field.ai.autoApply} label="auto update" />
                          </TableCell>
                          <TableCell>
                            <Flag value={field.ai.humanApprovalRequired} label="approval required" />
                          </TableCell>
                          <TableCell>
                            <Flag value={field.searchable} label="searchable" />
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              </Section>
            )
          })}
          {schema.data.rules.length > 0 && (
            <Section title="Cross-field rules" contentClassName="p-0">
              <ul className="divide-y text-sm">
                {schema.data.rules.map((rule) => (
                  <li key={rule.id} className="flex flex-col gap-0.5 px-4 py-2.5">
                    <span className="font-mono text-xs text-muted-foreground">
                      {rule.id} · {rule.type}
                    </span>
                    <span>{rule.description}</span>
                  </li>
                ))}
              </ul>
            </Section>
          )}
        </>
      )}
      <Sheet open={selected !== null} onOpenChange={(open) => !open && setSelected(null)}>
        <SheetContent side="right" className="w-full overflow-y-auto data-[side=right]:sm:max-w-lg">
          {selected && (
            <>
              <SheetHeader className="border-b">
                <SheetTitle>{selected.displayName}</SheetTitle>
                <SheetDescription className="font-mono text-xs">{selected.key}</SheetDescription>
              </SheetHeader>
              <div className="px-4 pb-6">
                <FieldDefinitionDetails field={selected} />
              </div>
            </>
          )}
        </SheetContent>
      </Sheet>
    </div>
  )
}
