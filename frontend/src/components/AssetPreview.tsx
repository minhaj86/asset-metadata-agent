import { useState } from 'react'
import { Box, File, FileText, Film, ImageOff } from 'lucide-react'
import { cn } from '@/lib/utils'

interface AssetPreviewProps {
  assetUrl: string
  mimeType: string
  alt: string
  className?: string
  /** small thumbnails hide the explanatory text */
  compact?: boolean
}

/**
 * Preview of the DAM-owned binary via its assetUrl. The UI never stores binaries; non-image types and unavailable
 * renditions get an explicit placeholder.
 */
export function AssetPreview({ assetUrl, mimeType, alt, className, compact }: AssetPreviewProps) {
  const [failed, setFailed] = useState(false)
  const isImage = mimeType.startsWith('image/')

  if (isImage && !failed) {
    return (
      <img
        src={assetUrl}
        alt={alt}
        loading="lazy"
        onError={() => setFailed(true)}
        className={cn('size-full bg-muted object-contain', className)}
      />
    )
  }
  const placeholder = failed
    ? { icon: ImageOff, label: 'Preview unavailable' }
    : mimeType.startsWith('video/')
      ? { icon: Film, label: 'Video — preview not supported' }
      : mimeType === 'application/pdf' || mimeType.startsWith('text/')
        ? { icon: FileText, label: 'Document — preview not supported' }
        : mimeType.startsWith('model/')
          ? { icon: Box, label: '3D model — preview not supported' }
          : { icon: File, label: 'No preview for this file type' }
  const Icon = placeholder.icon
  return (
    <div
      role="img"
      aria-label={`${alt}: ${placeholder.label}`}
      className={cn('flex size-full flex-col items-center justify-center gap-2 bg-muted text-muted-foreground', className)}
    >
      <Icon aria-hidden className={compact ? 'size-5' : 'size-8'} />
      {!compact && <span className="px-3 text-center text-xs">{placeholder.label}</span>}
    </div>
  )
}
