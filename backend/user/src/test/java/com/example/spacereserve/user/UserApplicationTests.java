package com.example.spacereserve.user;

import com.example.spacereserve.common.testsupport.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserApplicationTests {

	@Test
	void contextLoads() {
	}

}
