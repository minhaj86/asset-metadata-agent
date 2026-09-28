import type {
  NaturalLanguageSearchRequest,
  NaturalLanguageSearchResult,
  SearchResult,
  StructuredSearchRequest,
} from '@/types/api'
import { apiFetch } from './client'

/** Deterministic structured / hybrid search. */
export function structuredSearch(request: StructuredSearchRequest): Promise<SearchResult> {
  return apiFetch('/api/assets/search', { method: 'POST', body: request })
}

/** The SearchIntentAgent interprets the query; Spring validates and executes the criteria. */
export function naturalLanguageSearch(request: NaturalLanguageSearchRequest): Promise<NaturalLanguageSearchResult> {
  return apiFetch('/api/assets/semantic-search', { method: 'POST', body: request })
}
