/**
 * Spring Security への適合層。
 *
 * <p>
 * config に混ぜず分けているのは、ここに置くものが業務ロジックでも単なる設定でもなく、
 * 特定フレームワークの型（UserDetails、AuthenticationEntryPoint など）に依存した実装だから。 独立させておけば、認証方式を OIDC
 * へ移すときの変更範囲がこのパッケージにほぼ閉じる。
 *
 * <p>
 * フレームワークの型をこのパッケージの外へ持ち出さないこと。Controller が AppUserDetails を受け取るところまでは許すが、Service へ渡すのは
 * userId だけにする。 Service を HTTP と認証基盤から独立させておくため。
 *
 * <p>
 * 置くもの: AppUserDetails、AppUserDetailsService、CommonSecurityConfig（PasswordEncoder や
 * AuthenticationManager といった「誰であるか」を決める部品）、ApiSecurityDefaults（両アプリの
 * フィルタチェーンに共通する組み立て）。フィルタ層の 401/403 は標準ハンドラに任せており、 応答形式が Controller 由来のものと揃わない（理由は
 * authentication.md 8 節）。 詳細は docs/design/authentication.md の 7 節。
 *
 * <p>
 * SecurityFilterChain はここに置かない。「どの URL に誰を通すか」はアプリごとに違うので、
 * user.security.UserSecurityConfig と admin.security.AdminSecurityConfig がそれぞれ持つ。
 * 共通化してしまうと、片方に足したルールが黙ってもう片方にも効く。
 */
package com.example.spacereserve.common.security;
