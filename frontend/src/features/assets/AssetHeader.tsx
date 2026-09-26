import { Link } from 'react-router'
import { ArrowLeft, ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/PageHeader'
import { AiStatusBadge, AssetStatusBadge, AssetTypeBadge, RightsStatusBadge } from '@/components/status/StatusBadges'
import { RunEnrichmentButton } from '@/features/enrichment/RunEnrichmentButton'
import { useValidateAsset } from '@/features/metadata/useMetadataMutations'
import type { CatalogItem } from '@/types/api'

export function AssetHeader({ asset }: { asset: CatalogItem }) {
  const validate = useValidateAsset(asset.id)
  return (
    <PageHeader
      eyebrow={
        <Link to="/assets" className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground">
          <ArrowLeft aria-hidden className="size-3" />
          Assets
        </Link>
      }
      title={
        <span className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <span className="font-mono">{asset.externalAssetId}</span>
          <span className="truncate text-base font-normal text-muted-foreground">{asset.filename}</span>
        </span>
      }
      description={
        <span className="mt-1 flex flex-wrap items-center gap-1.5">
          <AssetTypeBadge type={asset.type} />
          <AssetStatusBadge status={asset.status} />
          <AiStatusBadge status={asset.aiStatus} />
          <RightsStatusBadge status={asset.rightsStatus} until={asset.summary?.usageRightsEnd} />
        </span>
      }
      actions={
        <>
          <Button size="sm" variant="outline" onClick={() => validate.mutate()} disabled={validate.isPending}>
            <ShieldCheck aria-hidden />
            Validate
          </Button>
          <RunEnrichmentButton asset={asset} />
        </>
      }
    />
  )
}
