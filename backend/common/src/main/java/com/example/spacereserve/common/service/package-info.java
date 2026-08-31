/**
 * 業務ルールとトランザクション境界を担う層。
 *
 * Transactional 注釈はこの層に付ける。Controller に付けると HTTP の都合と トランザクションの範囲が癒着し、Mapper に付けると複数の
 * Mapper をまたぐ操作を ひとつのトランザクションに収められない。参照のみのメソッドは readOnly = true にする。 MyBatis も Spring
 * のトランザクション管理にそのまま乗るため、書き方は JPA の場合と 変わらない。
 *
 * 状態を変えたら、必ず明示的に更新メソッドを呼ぶ。MyBatis にダーティチェックは無く、 取得したモデルを書き換えてもコミット時に UPDATE は飛ばない。JPA
 * から移ってきた目には 「動くはずのコードが黙って何もしない」形で現れるため、最も事故りやすい差分がここになる。
 *
 * user.changePassword(encoded); userMapper.updatePasswordHash(user); // ← これを忘れても例外は出ない
 *
 * 更新メソッドが返す件数を確認すること。0 件は「対象が消えている」か 「楽観ロックのバージョンが進んでいる」を意味する。放置すると更新の取りこぼしが 成功として通る。
 *
 * DTO からドメインモデルへの変換もこの層で行う。Controller は DTO しか知らず、 Mapper はドメインモデルしか知らない、という境界を保つため。
 *
 * 見つからない・状態が不正といった業務的な失敗は exception の例外を投げ、 HTTP ステータスへの変換は GlobalExceptionHandler
 * に任せる。Service が ResponseEntity や HttpStatus を知る必要はない。
 */
package com.example.spacereserve.common.service;
