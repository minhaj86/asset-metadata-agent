import type { MetadataValue } from '@/types/api'

const dateFormat = new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'short', day: '2-digit' })
const dateTimeFormat = new Intl.DateTimeFormat(undefined, {
  year: 'numeric',
  month: 'short',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})
const timeFormat = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' })
const relativeFormat = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value.length === 10 ? `${value}T00:00:00` : value)
  return Number.isNaN(date.getTime()) ? value : dateFormat.format(date)
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : dateTimeFormat.format(date)
}

export function formatTime(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : timeFormat.format(date)
}

export function formatRelative(value: string | null | undefined, now: Date = new Date()): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const seconds = Math.round((date.getTime() - now.getTime()) / 1000)
  const abs = Math.abs(seconds)
  if (abs < 60) {
    return relativeFormat.format(seconds, 'second')
  }
  if (abs < 3600) {
    return relativeFormat.format(Math.round(seconds / 60), 'minute')
  }
  if (abs < 86400) {
    return relativeFormat.format(Math.round(seconds / 3600), 'hour')
  }
  return relativeFormat.format(Math.round(seconds / 86400), 'day')
}

export function formatDuration(millis: number | null | undefined): string {
  if (millis === null || millis === undefined) {
    return '—'
  }
  if (millis < 1000) {
    return `${millis} ms`
  }
  const seconds = millis / 1000
  return seconds < 60 ? `${seconds.toFixed(1)} s` : `${Math.floor(seconds / 60)} min ${Math.round(seconds % 60)} s`
}

/** 0.957 -> "96%" */
export function formatConfidence(confidence: number | null | undefined): string {
  return confidence === null || confidence === undefined ? '—' : `${Math.round(confidence * 100)}%`
}

/** Percentage already in 0..100 -> "92%" (one decimal when needed). */
export function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return `${Number.isInteger(value) ? value : value.toFixed(1)}%`
}

export function formatNumber(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : new Intl.NumberFormat().format(value)
}

/** Plain text rendering of a metadata value (lists joined, booleans as Yes/No). */
export function formatValue(value: MetadataValue | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  if (Array.isArray(value)) {
    return value.length === 0 ? '—' : value.map((item) => formatValue(item)).join(', ')
  }
  if (typeof value === 'boolean') {
    return value ? 'Yes' : 'No'
  }
  return String(value)
}

export function isEmptyValue(value: MetadataValue | undefined): boolean {
  return value === null || value === undefined || value === '' || (Array.isArray(value) && value.length === 0)
}

/** "PRODUCT_IMAGE" -> "Product image"; "angleFacing" -> "Angle facing" */
export function humanize(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  const words = value.includes('_')
    ? value.toLowerCase().split('_')
    : value.replace(/([a-z0-9])([A-Z])/g, '$1 $2').toLowerCase().split(' ')
  const text = words.filter(Boolean).join(' ')
  return text.charAt(0).toUpperCase() + text.slice(1)
}

/** "metadataExtractionAgent" -> "Metadata Extraction Agent" */
export function agentName(agent: string | null | undefined): string {
  if (!agent) {
    return '—'
  }
  const name = agent.startsWith('ai:') ? agent.slice(3) : agent
  return name.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/^./, (first) => first.toUpperCase())
}

export function pluralize(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`
}
