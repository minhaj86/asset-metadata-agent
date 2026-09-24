import { useSyncExternalStore } from 'react'
import { getCurrentUser, setCurrentUser, subscribeCurrentUser } from '@/api/currentUser'

/** Reviewer identity sent as X-User-Id (identity-provider stand-in). */
export function useCurrentUser(): [string, (userId: string) => void] {
  const user = useSyncExternalStore(subscribeCurrentUser, getCurrentUser, getCurrentUser)
  return [user, setCurrentUser]
}
