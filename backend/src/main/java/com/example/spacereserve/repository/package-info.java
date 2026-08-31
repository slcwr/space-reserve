/**
 * 永続化を担う層。MyBatis の Mapper インターフェースを置く。
 *
 * クラス名は XxxMapper、Mapper 注釈を付けたインターフェースとして定義し、 実装クラスは書かない（MyBatis が実行時に生成する）。層としての役割は JPA
 * を使う場合と 同じなので、パッケージ名は repository のままにしてある。
 *
 * SQL の置き場は、長さではなく構造で決める。
 *
 * - 単一テーブルに対する素直な1文（静的な SELECT / INSERT / UPDATE / DELETE）は Select などの注釈でインターフェースに書く。
 *
 * - 動的 SQL（if, foreach）、結合、association や明示的な resultMap が要るものは
 * src/main/resources/mapper/XxxMapper.xml に置く。これらは注釈では script 文字列や SelectProvider
 * を経由することになり、XML より読めなくなる。
 *
 * 行数で線を引かないこと。列が1本増えただけで引っ越し対象が変わり、判断が揺れる。 「XML でしか素直に書けないか」で切れば、迷う場面がほとんど無くなる。
 *
 * 混在する以上、SQL の全量を追うには2箇所を見ることになる。注釈側は repository パッケージへの Select|Insert|Update|Delete の
 * grep、XML 側は resources/mapper 配下の全ファイルで、どちらも機械的に列挙できる状態を保つこと。
 *
 * 同じメソッドを注釈と XML の両方に書かないこと。起動時に「Mapped Statements collection already contains value for
 * ...」で失敗する。XML へ移すときは注釈を消す。
 *
 * XML の namespace は Mapper インターフェースの完全修飾名と一致させる。ずれると 起動時ではなくそのメソッドを呼んだ時点で
 * BindingException になる。
 *
 * 引数が2つ以上あるメソッドは各引数に Param 注釈を付ける。省略すると XML から #{param1} でしか参照できず、意味が読めない SQL になる。
 *
 * 戻り値の型は domain のモデルか、その List。単一件の取得は Optional を使ってよい （MyBatis が対応している）。
 *
 * 更新系メソッドの戻り値 int（更新件数）を捨てないこと。MyBatis にダーティチェックは
 * 無く、楽観ロックも自動では効かない。「更新したつもりで0件だった」を検出できるのは、 呼び出し側でこの件数を見たときだけになる。
 *
 * ここに業務判断を持ち込まないこと。「予約可能な部屋を返す」ではなく 「指定時間帯に予約が存在しない部屋を返す」という、検索条件としての語彙で書く。
 */
package com.example.spacereserve.repository;
