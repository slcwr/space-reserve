import type { ApiFailure } from '@/shared/api'

/** サーバの `detail` は開発者向けなので使わず、表示文言はフロントが持つ。 */
export function loginErrorMessage(failure: ApiFailure): string {
  switch (failure.status) {
    case 400:
      return '入力内容を確認してください。'
    case 401:
      // どちらが違うかは返さない。アドレスの存在を確かめる手段になる。
      return 'メールアドレスまたはパスワードが違います。'
    case 403:
      return 'ページを再読み込みしてから、もう一度お試しください。'
    case 429:
      // 残り時間は出さない。制限中のアカウントが割れる。
      return '試行回数が上限に達しました。しばらく時間をおいて再度お試しください。'
    default:
      return '処理に失敗しました。時間をおいて再度お試しください。'
  }
}
