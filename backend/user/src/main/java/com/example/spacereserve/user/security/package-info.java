/**
 * 利用者向けアプリの認可ルール。
 *
 * <p>
 * 認証の部品（UserDetails、PasswordEncoder、AuthenticationManager）は common.security にあり、 ここには
 * SecurityFilterChain だけを置く。「どの URL に誰を通すか」はアプリごとに違うため。
 */
package com.example.spacereserve.user.security;
