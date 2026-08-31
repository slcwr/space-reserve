package com.example.spacereserve;

import org.springframework.boot.SpringApplication;

public class TestSpaceReserveApplication {

	public static void main(String[] args) {
		SpringApplication.from(SpaceReserveApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
