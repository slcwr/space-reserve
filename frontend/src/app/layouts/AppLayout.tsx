import { Link, Outlet } from 'react-router'
import { useCurrentUser, useLogout } from '@/authentication'

export function AppLayout() {
  const { data: user } = useCurrentUser()
  const { mutate: logout, isPending } = useLogout()

  return (
    <div className="app-layout">
      <header className="app-layout__header">
        <Link to="/" className="app-layout__brand">
          space-reserve
        </Link>
        <div className="app-layout__account">
          <span>{user?.displayName}</span>
          <button type="button" onClick={() => logout()} disabled={isPending}>
            ログアウト
          </button>
        </div>
      </header>
      <main className="app-layout__main">
        <Outlet />
      </main>
    </div>
  )
}
