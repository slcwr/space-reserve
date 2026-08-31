import { Navigate, Outlet } from 'react-router'
import { PageMessage } from '@/shared/components'
import { useCurrentUser } from '../hooks/useCurrentUser'

/** 画面を出すかどうかだけを見る。保護そのものはサーバ側の API で完結している。 */
export function RequireAuth() {
  const { data: user, isPending } = useCurrentUser()

  if (isPending) {
    return <PageMessage>読み込んでいます…</PageMessage>
  }

  if (!user) {
    return <Navigate to="/login" replace />
  }

  return <Outlet />
}
