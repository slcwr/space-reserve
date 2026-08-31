import { currentUserQueryKey } from '@/authentication'
import { setSessionExpiredHandler } from '@/shared/api'
import { queryClient } from './queryClient'
import { router } from './router'

/** セッション切れ（401）時の遷移を配線する。描画より前に呼ぶこと。 */
export function registerSessionExpiry(): void {
  setSessionExpiredHandler(() => {
    // 先に落とさないと RequireAuth が古い値を見て元の画面へ押し戻される。
    queryClient.setQueryData(currentUserQueryKey, null)
    void router.navigate('/login')
  })
}
