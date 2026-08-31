import { useQuery } from '@tanstack/react-query'
import { fetchCurrentUser } from '../api/authApi'

export const currentUserQueryKey = ['authentication', 'currentUser'] as const

/** ログイン中の利用者。未ログインなら `data` は `null`。状態が変わる契機では必ずこのキャッシュを書き換えるため再取得しない。 */
export function useCurrentUser() {
  return useQuery({
    queryKey: currentUserQueryKey,
    queryFn: fetchCurrentUser,
    staleTime: Number.POSITIVE_INFINITY,
    retry: false,
  })
}
