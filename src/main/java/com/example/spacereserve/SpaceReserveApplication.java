package com.example.spacereserve;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * ConfigurationPropertiesScan を付けているのは、config パッケージの ConfigurationProperties を record のまま
 * Bean にするため。これが無いと注釈だけでは登録されず、注入時に解決できない。
 */
@ConfigurationPropertiesScan
@SpringBootApplication
public class SpaceReserveApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpaceReserveApplication.class, args);
	}

}
