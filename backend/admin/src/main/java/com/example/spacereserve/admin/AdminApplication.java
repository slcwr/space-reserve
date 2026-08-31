package com.example.spacereserve.admin;

import org.mybatis.spring.annotation.MapperScan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 管理向けアプリの起動クラス。user アプリとは別プロセスで、既定では 8081 で待ち受ける。
 *
 * 走査対象を明示する理由は UserApplication と同じ（common が別 jar にあるため）。
 *
 * user アプリを走査対象に含めないこと。依存関係としても含まれていないが、仮に classpath へ 現れても管理アプリが利用者向けの Controller
 * を公開してよい理由にはならない。
 */
@SpringBootApplication(scanBasePackages = { "com.example.spacereserve.common", "com.example.spacereserve.admin" })
@ConfigurationPropertiesScan({ "com.example.spacereserve.common", "com.example.spacereserve.admin" })
@MapperScan("com.example.spacereserve.common.repository")
public class AdminApplication {

	public static void main(String[] args) {
		SpringApplication.run(AdminApplication.class, args);
	}

}
