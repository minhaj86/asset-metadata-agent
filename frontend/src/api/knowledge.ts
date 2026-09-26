import type { KnowledgeAnswer } from '@/types/api'
import { apiFetch } from './client'

/** RAG over the metadata governance documentation; answers UNKNOWN when the documentation does not cover it. */
export function askKnowledge(question: string): Promise<KnowledgeAnswer> {
  return apiFetch('/api/knowledge/ask', { method: 'POST', body: { question } })
}
