package com.example.rubun.sharded_wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShardedWalletApplication {

	public static void main(String[] args) {

		SpringApplication.run(ShardedWalletApplication.class, args);
		System.out.println("Spring is up and running");
	}

}
