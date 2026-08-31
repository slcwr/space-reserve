package com.example.spacereserve.user;

import org.mybatis.spring.annotation.MapperScan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 利用者向けアプリの起動クラス。
 *
 * 走査対象を3種類とも明示しているのは、common が別パッケージ（別 jar）にあるため。 いずれも既定ではこのクラスのパッケージ配下しか見ないので、省略すると
 * common 側の Bean が 黙って登録されないまま起動し、注入時に初めて失敗する。
 *
 * - scanBasePackages: Component / Service / RestController の走査範囲 -
 * ConfigurationPropertiesScan: ConfigurationProperties を record のまま Bean にする -
 * MapperScan: MyBatis の Mapper インターフェース（既定は自動設定パッケージ＝このクラスの パッケージ配下のみで、scanBasePackages
 * を書いても連動しない）
 */
@SpringBootApplication(scanBasePackages = { "com.example.spacereserve.common", "com.example.spacereserve.user" })
@ConfigurationPropertiesScan({ "com.example.spacereserve.common", "com.example.spacereserve.user" })
@MapperScan("com.example.spacereserve.common.repository")
public class UserApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserApplication.class, args);
	}

}
