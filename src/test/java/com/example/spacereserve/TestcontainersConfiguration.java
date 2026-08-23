package com.example.spacereserve;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
	}

	/**
	 * Testcontainers 2.x に Redis 専用モジュールは無いため、コアの {@code GenericContainer}
	 * を使う。{@code MySQLContainer} と違い型から接続の種類を 推論できないので、{@code @ServiceConnection}
	 * にサービス名を明示する必要がある。
	 */
	@Bean
	@ServiceConnection("redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:8.10")).withExposedPorts(6379);
	}

}
