/**
 * Reviewer identity used for the X-User-Id header (stand-in for an identity provider). The backend decides what an
 * identity may do; the UI only stores which identity the current user works under.
 */
const STORAGE_KEY = 'dam.reviewerId'
export const DEFAULT_USER = 'jane.doe'

const listeners = new Set<() => void>()
let current = read()

function read(): string {
  try {
    return window.localStorage.getItem(STORAGE_KEY)?.trim() || DEFAULT_USER
  } catch {
    return DEFAULT_USER
  }
}

export function getCurrentUser(): string {
  return current
}

export function setCurrentUser(userId: string): void {
  current = userId.trim() || DEFAULT_USER
  try {
    window.localStorage.setItem(STORAGE_KEY, current)
  } catch {
    // storage unavailable (private mode): keep the in-memory value
  }
  listeners.forEach((listener) => listener())
}

export function subscribeCurrentUser(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
