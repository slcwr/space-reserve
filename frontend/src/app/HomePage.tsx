import { useCurrentUser } from '@/authentication'

export function HomePage() {
  const { data: user } = useCurrentUser()

  return (
    <section>
      <h1>ようこそ{user ? `、${user.displayName} さん` : ''}</h1>
      <p>予約・スペース・利用者の画面はこれから作ります。</p>
    </section>
  )
}
