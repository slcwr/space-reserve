import { QueryClient } from '@tanstack/react-query'

/** React の外（sessionExpiry.ts）からも触るためモジュールから公開する。画面では useQueryClient() を使う。 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
      refetchOnWindowFocus: false,
    },
  },
})
