/**
 * ドメインモデル。永続化フレームワークを知らない素の Java オブジェクトを置く。
 *
 * MVC の「M」にあたるが、DB のテーブル定義をそのまま写した器にしないこと。
 * 「この予約はキャンセルできるか」のように、そのオブジェクト自身のデータだけで判断できる
 * ルールはメソッドとして持たせる。複数のモデルや外部の状態が必要になる判断は
 * service 側に置く。
 *
 * MyBatis の注釈をここに書かない。SQL と列のマッピングは
 * src/main/resources/mapper の XML が持ち、このパッケージのクラスはその存在を知らない。
 * JPA と違い永続化のための注釈が一切要らないのが MyBatis を選んだ利点なので、
 * これを崩さない。
 *
 * クラスの形は次に揃える。MyBatis がインスタンスを組み立てられる範囲で、
 * アプリケーションから見た可変性を最小にするための約束。
 *
 *   - record ではなく class にする。
 *     record はコンストラクタマッピング（<constructor>）が必要になり、
 *     テーブルごとの記述量が増える。dto は record、domain は class という使い分けにする。
 *
 *   - setter を書かない。
 *     MyBatis は setter が無ければフィールドへ直接代入するため、getter だけで動く。
 *     ただし final フィールドは代入できない。
 *
 *   - 引数なしコンストラクタを private で用意する。
 *     MyBatis はこれを setAccessible して呼ぶ。アプリケーションからの生成は
 *     static ファクトリメソッド（User.signUp(...) など）に寄せる。
 *
 *   - equals / hashCode は必要なら ID で定義する。
 *     MyBatis に永続化コンテキストは無く、同じ行を2回読めば別インスタンスになる。
 *     JPA のように同一性が保証されている前提でコードを書かないこと。
 *
 * 列挙（Role など）は組み込みの EnumTypeHandler が VARCHAR と列挙定数名を
 * 相互変換するため、設定は要らない。値オブジェクトを複数カラムから組み立てる場合は、
 * XML 側の <association> か専用の TypeHandler で対応する。
 *
 * スキーマは src/main/resources/db/migration を正とする。
 * MyBatis には起動時にスキーマとモデルの整合を検証する仕組みが無いため、Hibernate の
 * ddl-auto: validate が担っていた早期検出は失われている。ずれは実際にその SQL が
 * 走った時点で初めて表面化する。モデルを変えたら同じコミットでマイグレーションと
 * XML の両方を直すこと。
 */
package com.example.spacereserve.domain;
