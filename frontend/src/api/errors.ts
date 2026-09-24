import type { Issue } from '@/types/api'
import { ApiError } from './client'

/** Problem codes the backend uses when the optional AI capability cannot serve a request. */
const AI_UNAVAILABLE_CODES = new Set([
  'AI_UNAVAILABLE',
  'AI_DISABLED',
  'AI_TIMEOUT',
  'AI_WORKFLOW_FAILED',
  'AI_INTERRUPTED',
])

export function isAiUnavailable(error: unknown): boolean {
  return error instanceof ApiError && (AI_UNAVAILABLE_CODES.has(error.code ?? '') || error.status === 503)
}

export function isNetworkError(error: unknown): boolean {
  return error instanceof ApiError && error.status === 0
}

export function validationIssues(error: unknown): Issue[] {
  return error instanceof ApiError ? error.issues : []
}

/** A short, user-facing description. Never includes stack traces; falls back to a generic message. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (isAiUnavailable(error)) {
      return `AI service unavailable. ${error.message}`
    }
    if (error.issues.length > 0) {
      return error.issues.map((issue) => issue.message).join(' ')
    }
    return error.message
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return 'Something went wrong. Please try again.'
}

export function errorTitle(error: unknown): string {
  if (isNetworkError(error)) {
    return 'Backend not reachable'
  }
  if (isAiUnavailable(error)) {
    return 'AI service unavailable'
  }
  if (error instanceof ApiError) {
    switch (error.status) {
      case 403:
        return 'Not permitted'
      case 404:
        return 'Not found'
      case 409:
        return 'Conflict'
      case 422:
        return 'Metadata update rejected'
      default:
        return error.problem.title ?? 'Request failed'
    }
  }
  return 'Request failed'
}
