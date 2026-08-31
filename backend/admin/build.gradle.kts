dependencies {
	implementation(project(":common"))
	developmentOnly("org.springframework.boot:spring-boot-devtools")

	// Flyway を入れないのは意図的。スキーマの適用主体は user モジュールに寄せてある。
	// 両方が起動時にマイグレーションを走らせると、同時起動でロック待ちや競合が起きる。

	testImplementation(testFixtures(project(":common")))
	testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:4.0.1")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-session-data-redis-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
