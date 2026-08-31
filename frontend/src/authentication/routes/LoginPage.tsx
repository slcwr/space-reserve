import { Navigate } from 'react-router'
import { PageMessage } from '@/shared/components'
import { LoginForm } from '../components/LoginForm'
import { useCurrentUser } from '../hooks/useCurrentUser'

export function LoginPage() {
  const { data: user, isPending } = useCurrentUser()

  if (isPending) {
    return <PageMessage>読み込んでいます…</PageMessage>
  }

  if (user) {
    return <Navigate to="/" replace />
  }

  return (
    <main className="login-page">
      <h1>space-reserve</h1>
      <LoginForm />
    </main>
  )
}
