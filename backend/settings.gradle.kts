rootProject.name = "space-reserve"

// common はライブラリ、user と admin はそれぞれ独立した Spring Boot アプリ。
// 依存は user → common / admin → common の一方向だけで、user と admin は互いを知らない。
// この向きは build.gradle.kts の依存宣言が強制するので、規約ではなくビルドが守る。
include("common")
include("user")
include("admin")
