import type { TaxonomyNode, TaxonomyUsage } from '@/types/api'
import { apiFetch } from './client'

export function fetchTaxonomyNodes(signal?: AbortSignal): Promise<TaxonomyNode[]> {
  return apiFetch('/api/taxonomy/nodes', { signal })
}

export function searchTaxonomy(query: string, signal?: AbortSignal): Promise<TaxonomyNode[]> {
  return apiFetch('/api/taxonomy/search', { query: { q: query, limit: 10 }, signal })
}

/** Assets per taxonomy node, exact and including descendants. */
export function fetchTaxonomyUsage(signal?: AbortSignal): Promise<TaxonomyUsage[]> {
  return apiFetch('/api/catalog/taxonomy-usage', { signal })
}
