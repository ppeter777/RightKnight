package dev.rightknight;

import dev.rightknight.service.GameAnalysisService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;

@ConfigurationPropertiesScan
@SpringBootApplication
@EnableAsync
public class RightKnightApplication {

	public static void main(String[] args) {

		ApplicationContext context = SpringApplication.run(RightKnightApplication.class, args);

		GameAnalysisService gameAnalysisService = context.getBean(GameAnalysisService.class);

		String gameId = "zx316:0etNigW3";

		gameAnalysisService.analyzeGame(gameId);

		SpringApplication.run(RightKnightApplication.class, args);

	}
}
