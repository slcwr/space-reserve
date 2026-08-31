import { useState, type FormEvent } from 'react'
import { toApiFailure } from '@/shared/api'
import { useLogin } from '../hooks/useLogin'
import { loginErrorMessage } from '../model/loginError'

export function LoginForm() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const { mutate, isPending, error } = useLogin()

  const failure = error ? toApiFailure(error) : undefined

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    mutate({ email, password })
  }

  return (
    <form className="login-form" onSubmit={handleSubmit} noValidate>
      {failure && (
        <p className="login-form__error" role="alert">
          {loginErrorMessage(failure)}
        </p>
      )}

      <label className="login-form__field">
        <span>メールアドレス</span>
        <input
          type="email"
          name="email"
          value={email}
          autoComplete="username"
          onChange={(event) => setEmail(event.target.value)}
          required
        />
        {failure?.fieldErrors.email && <small>{failure.fieldErrors.email}</small>}
      </label>

      <label className="login-form__field">
        <span>パスワード</span>
        <input
          type="password"
          name="password"
          value={password}
          autoComplete="current-password"
          onChange={(event) => setPassword(event.target.value)}
          required
        />
        {failure?.fieldErrors.password && <small>{failure.fieldErrors.password}</small>}
      </label>

      <button type="submit" disabled={isPending}>
        {isPending ? 'ログインしています…' : 'ログイン'}
      </button>
    </form>
  )
}
