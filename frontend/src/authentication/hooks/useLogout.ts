import { useMutation, useQueryClient } from '@tanstack/react-query'
import { logout } from '../api/authApi'
import { currentUserQueryKey } from './useCurrentUser'

export function useLogout() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      // 順序が逆だと `/auth/me` の再取得が走って無駄な 401 を1回踏む。
      queryClient.clear()
      queryClient.setQueryData(currentUserQueryKey, null)
    },
  })
}
