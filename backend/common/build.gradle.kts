plugins {
	`java-library`
	// Testcontainers の起動定義を user / admin の両方から使えるようにする。
	// テスト用クラスを main に置かず、かといって重複させないための仕組み。
	`java-test-fixtures`
}

// common はアプリではないので実行可能 jar を作らない。代わりに素の jar を出す
// （Boot プラグインを適用すると bootJar が有効・jar が無効になるため、両方を明示的に戻す）。
tasks.bootJar {
	enabled = false
}

tasks.jar {
	enabled = true
}

dependencies {
	// user / admin のコードが直接触れる型（AppUserDetails、User、ProblemDetail など）を
	// 含む依存は api で公開する。implementation にすると利用側のコンパイルが通らない。
	api("org.springframework.boot:spring-boot-starter-security")
	api("org.springframework.boot:spring-boot-starter-webmvc")
	api("org.springframework.boot:spring-boot-starter-validation")
	// Boot 3 系では data-redis と spring-session-data-redis の2本が必要だったが、
	// 4.x ではこの starter 1本にまとまっている。
	api("org.springframework.boot:spring-boot-starter-session-data-redis")
	// ORM は MyBatis。Boot の BOM 管理外なのでバージョンを明記する。
	// 4.0 系が Spring Boot 4.0 以上 / Java 17 以上に対応する。
	api("org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.1")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	// JDBC ドライバは実行時のみ。runtimeOnly は利用側の実行時クラスパスにも伝播するので、
	// user / admin で書き直す必要はない。
	runtimeOnly("com.mysql:mysql-connector-j")

	// テストフィクスチャ（TestcontainersConfiguration）が公開する型。利用側の
	// テストコードから MySQLContainer などを参照できるよう api で出す。
	// TestConfiguration 注釈のために core のテスト starter が要る。技術ごとの
	// starter（webmvc-test など）は各アプリ側が持つ。
	testFixturesApi("org.springframework.boot:spring-boot-starter-test")
	testFixturesApi("org.springframework.boot:spring-boot-testcontainers")
	testFixturesApi("org.testcontainers:testcontainers-junit-jupiter")
	testFixturesApi("org.testcontainers:testcontainers-mysql")
}
