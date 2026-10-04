package dev.rightknight;

import dev.rightknight.analysis.ExpectedMoveTimeCalculator;
import dev.rightknight.analysis.MoveAssessment;
import dev.rightknight.analysis.MoveQualityClassifier;
import dev.rightknight.repository.GameMoveAnalysisRepository;
import dev.rightknight.repository.GameMoveRepository;
import dev.rightknight.repository.GameRepository;
import dev.rightknight.service.GameAnalysisService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.ArrayList;
import java.util.List;

@ConfigurationPropertiesScan
@SpringBootApplication
@EnableAsync
public class RightKnightApplication {

	public static void main(String[] args) {

		ApplicationContext context = SpringApplication.run(RightKnightApplication.class, args);
		
	}
}
