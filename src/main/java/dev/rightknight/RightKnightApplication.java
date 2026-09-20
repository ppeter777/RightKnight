package dev.rightknight;

import dev.rightknight.model.GameMoveAnalysisEntity;
import dev.rightknight.model.GameMoveEntity;
import dev.rightknight.service.MoveAnalysis;
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


//
//
		ApplicationContext context = SpringApplication.run(RightKnightApplication.class, args);

		MoveAnalysis moveAnalysis = context.getBean(MoveAnalysis.class);
////
//		String positionFen = "7k/8/8/6Pp/8/8/8/7K w - h6 0 1";
//
//		var a = moveAnalysis.analyzeMove(15993L);
//
//		var b = 0;
//
//
//
//
//
//
//
//

		GameMoveEntity move = new GameMoveEntity();

		move.setUci("f1e1");
		move.setFenBefore(
				"r3k2r/pppq1ppp/1b1p1n2/4p3/2N1P3/2PP1Q1P/PP3PP1/R1B1R1K1 b kq - 0 11"
		);
		move.setFenAfter(
				"r3k2r/pp1q1ppp/1bpp1n2/4p3/2N1P3/2PP1Q1P/PP3PP1/R1B1R1K1 w kq - 0 11"
		);
		move.setWhiteMove(true);

		GameMoveAnalysisEntity result = moveAnalysis.analyzeMove(move);

		var a = 3;


		SpringApplication.run(RightKnightApplication.class, args);
	}
}
