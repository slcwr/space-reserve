import axios from 'axios'

/** RFC 9457 の Problem Details。 */
export type ProblemDetails = {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  errors?: Record<string, string>
}

export type ApiFailure = {
  status: number | undefined
  problem: ProblemDetails | undefined
  /** 項目名 → 違反メッセージ。400 のときだけ入る。 */
  fieldErrors: Record<string, string>
}

function isProblemDetails(data: unknown): data is ProblemDetails {
  return typeof data === 'object' && data !== null && !Array.isArray(data)
}

/** 例外を画面が扱える形へ均す。401 はボディが無く 403 は Boot 既定形式のため `problem` は undefined になり得る。 */
export function toApiFailure(error: unknown): ApiFailure {
  if (!axios.isAxiosError(error)) {
    return { status: undefined, problem: undefined, fieldErrors: {} }
  }

  const data: unknown = error.response?.data
  const problem = isProblemDetails(data) ? data : undefined

  return {
    status: error.response?.status,
    problem,
    fieldErrors: problem?.errors ?? {},
  }
}
