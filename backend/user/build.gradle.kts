dependencies {
	implementation(project(":common"))
	// Flyway はこのモジュールにだけ置く。マイグレーションの実行主体を1つに絞るため
	// （SQL 自体は common/src/main/resources/db/migration にあり、両アプリが共有する）。
	// Boot 4 では自動設定が技術ごとのモジュールに分かれたため、flyway-core を直接
	// 指定しても Flyway は起動しない。starter を経由すること。
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	// Flyway 10 以降は DB ごとのサポートが別モジュールに分かれている。
	implementation("org.flywaydb:flyway-mysql")
	developmentOnly("org.springframework.boot:spring-boot-devtools")

	testImplementation(testFixtures(project(":common")))
	testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:4.0.1")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-session-data-redis-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
