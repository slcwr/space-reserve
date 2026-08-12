/**
 * 永続化を担う層。
 *
 * <p>基本は {@code JpaRepository<Reservation, Long>} を継承したインターフェースのみ。
 * 実装クラスは書かない。命名規約で足りない検索は {@code @Query} を使い、それでも
 * 表現しづらい動的な条件は Specification か、専用の {@code XxxRepositoryCustom} を切る。
 *
 * <p>ここに業務判断を持ち込まないこと。「予約可能な部屋を返す」ではなく
 * 「指定時間帯に予約が存在しない部屋を返す」という、検索条件としての語彙で書く。
 */
package com.example.spacereserve.repository;
