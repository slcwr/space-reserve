plugins {
	// ルートは成果物を持たない。プラグインをクラスパスへ載せるだけで、適用は subprojects で行う。
	id("org.springframework.boot") version "4.1.0" apply false
	id("io.spring.dependency-management") version "1.1.7" apply false
	// 整形は Spring Boot 本体と同じ spring-javaformat に揃える。設定項目は持たず、
	// タブ4・120桁・import 順が規約として固定される。`./gradlew format` で整形し、
	// `checkFormat` が check に紐づくので崩れたままコミットしても test で気づける。
	id("io.spring.javaformat") version "0.0.48" apply false
}

subprojects {
	apply(plugin = "java")
	// common にも Boot プラグインを適用する。狙いは jar 化ではなく、これが有効にする
	// dependency-management の BOM で全モジュールの Spring 系バージョンを揃えること。
	// common 側の bootJar は common/build.gradle.kts で無効化する。
	apply(plugin = "org.springframework.boot")
	apply(plugin = "io.spring.dependency-management")
	apply(plugin = "io.spring.javaformat")

	group = "com.example"
	version = "0.0.1-SNAPSHOT"

	repositories {
		mavenCentral()
	}

	configure<JavaPluginExtension> {
		toolchain {
			languageVersion = JavaLanguageVersion.of(21)
		}
	}

	dependencies {
		// Lombok は注釈プロセッサなのでモジュールごとに要る。common に置いても伝播しない。
		"compileOnly"("org.projectlombok:lombok:1.18.46")
		"annotationProcessor"("org.projectlombok:lombok:1.18.46")
		"testCompileOnly"("org.projectlombok:lombok:1.18.46")
		"testAnnotationProcessor"("org.projectlombok:lombok:1.18.46")
		"testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
	}

	tasks.withType<Test> {
		useJUnitPlatform()
	}
}
