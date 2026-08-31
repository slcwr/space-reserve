import { useMutation, useQueryClient } from '@tanstack/react-query'
import { login } from '../api/authApi'
import { currentUserQueryKey } from './useCurrentUser'

export function useLogin() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: login,
    onSuccess: (user) => {
      queryClient.setQueryData(currentUserQueryKey, user)
    },
  })
}
