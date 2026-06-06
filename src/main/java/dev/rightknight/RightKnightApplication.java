package dev.rightknight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RightKnightApplication {

	public static void main(String[] args) {
		SpringApplication.run(RightKnightApplication.class, args);
	}

}
