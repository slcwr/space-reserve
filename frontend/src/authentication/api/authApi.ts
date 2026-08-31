import { apiClient, toApiFailure } from '@/shared/api'
import type { User } from '../model/user'

export type LoginCommand = {
  email: string
  password: string
}

/** 資格情報の誤りによる 401 はセッション切れではない。 */
export async function login(command: LoginCommand): Promise<User> {
  const { data } = await apiClient.post<User>('/auth/login', command, {
    skipSessionExpiredHandler: true,
  })
  return data
}

export async function logout(): Promise<void> {
  await apiClient.post('/auth/logout')
}

/**
 * ログイン中の利用者。未ログインなら `null`（401 は正常な答え）。
 * 開発時は `GET /` が Vite 止まりで `XSRF-TOKEN` が撒かれないため、起動時のこの1回が最初のログイン POST の 403 を防ぐ。
 */
export async function fetchCurrentUser(): Promise<User | null> {
  try {
    const { data } = await apiClient.get<User>('/auth/me', {
      skipSessionExpiredHandler: true,
    })
    return data
  } catch (error) {
    if (toApiFailure(error).status === 401) {
      return null
    }
    throw error
  }
}
