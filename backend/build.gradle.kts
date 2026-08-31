plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	// 整形は Spring Boot 本体と同じ spring-javaformat に揃える。設定項目は持たず、
	// タブ4・120桁・import 順が規約として固定される。`./gradlew format` で整形し、
	// `checkFormat` が check に紐づくので崩れたままコミットしても test で気づける。
	id("io.spring.javaformat") version "0.0.48"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	// ORM は MyBatis。Boot の BOM 管理外なのでバージョンを明記する。
	// 4.0 系が Spring Boot 4.0 以上 / Java 17 以上に対応する。
	implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.1")
	implementation("org.springframework.boot:spring-boot-starter-security")
	// Boot 3 系では data-redis と spring-session-data-redis の2本が必要だったが、
	// 4.x ではこの starter 1本にまとまっている。
	implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	// Boot 4 では自動設定が技術ごとのモジュールに分かれたため、flyway-core を直接
	// 指定しても Flyway は起動しない。starter を経由すること。
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	// Flyway 10 以降は DB ごとのサポートが別モジュールに分かれている。
	implementation("org.flywaydb:flyway-mysql")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("com.mysql:mysql-connector-j")
	testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:4.0.1")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-session-data-redis-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-mysql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
