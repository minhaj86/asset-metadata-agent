import type { Issue, ProblemDetail } from '@/types/api'
import { getCurrentUser } from './currentUser'

/** Error raised for non-2xx responses; carries the backend's problem details (code, issues, correlation id). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined
  readonly problem: ProblemDetail

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail || problem.title || `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = problem.code
    this.problem = problem
  }

  get issues(): Issue[] {
    return this.problem.issues ?? []
  }
}

export type QueryValue = string | number | boolean | null | undefined | string[]

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  query?: Record<string, QueryValue>
  body?: unknown
  headers?: Record<string, string>
  signal?: AbortSignal
}

export function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const params = new URLSearchParams()
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') {
      return
    }
    if (Array.isArray(value)) {
      value.filter((item) => item !== '').forEach((item) => params.append(key, item))
    } else {
      params.append(key, String(value))
    }
  })
  const search = params.toString()
  return search ? `${path}?${search}` : path
}

async function send(path: string, options: RequestOptions, accept: string): Promise<Response> {
  const headers: Record<string, string> = {
    Accept: accept,
    'X-User-Id': getCurrentUser(),
    ...options.headers,
  }
  let body: BodyInit | undefined
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.body)
  }
  let response: Response
  try {
    response = await fetch(buildUrl(path, options.query), {
      method: options.method ?? 'GET',
      headers,
      body,
      signal: options.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    throw new ApiError(0, {
      code: 'NETWORK_ERROR',
      title: 'Backend not reachable',
      detail: 'The DAM backend could not be reached. Check that the Spring Boot application is running.',
    })
  }
  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response))
  }
  return response
}

async function readProblem(response: Response): Promise<ProblemDetail> {
  const type = response.headers.get('Content-Type') ?? ''
  if (type.includes('json')) {
    try {
      return (await response.json()) as ProblemDetail
    } catch {
      // fall through to a generic problem
    }
  }
  return { status: response.status, title: response.statusText || 'Request failed' }
}

/** JSON request against the Spring Boot API. All AI calls happen server-side; the browser never sees model keys. */
export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options, 'application/json')
  if (response.status === 204) {
    return undefined as T
  }
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export interface DownloadedFile {
  blob: Blob
  filename: string
}

/** Request that returns a file (e.g. metadata export). */
export async function apiDownload(path: string, options: RequestOptions = {}): Promise<DownloadedFile> {
  const response = await send(path, { method: 'POST', ...options }, '*/*')
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const match = /filename="?([^";]+)"?/i.exec(disposition)
  return { blob: await response.blob(), filename: match?.[1] ?? 'download' }
}
