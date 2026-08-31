import axios from 'axios'

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** この要求の 401 をセッション切れとして扱わない。 */
    skipSessionExpiredHandler?: boolean
  }
}

export const apiClient = axios.create({
  baseURL: '/api',
  // CSRF がこの2つの名前に依存している（サーバは CookieCsrfTokenRepository.withHttpOnlyFalse()）。
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

type SessionExpiredHandler = () => void

let onSessionExpired: SessionExpiredHandler = () => {}

/** 遷移先の決定は app/ の仕事。ここでルータへ直接触ると shared が app に依存する。 */
export function setSessionExpiredHandler(handler: SessionExpiredHandler): void {
  onSessionExpired = handler
}

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (
      axios.isAxiosError(error) &&
      error.response?.status === 401 &&
      !error.config?.skipSessionExpiredHandler
    ) {
      onSessionExpired()
    }
    return Promise.reject(error)
  },
)
