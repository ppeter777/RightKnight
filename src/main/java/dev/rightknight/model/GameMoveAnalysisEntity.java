package dev.rightknight.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "game_move_analysis",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_game_move_analysis_run_move",
                columnNames = {"game_analysis_id", "game_move_id"}
        )
)
@Getter
@Setter
public class GameMoveAnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_analysis_id", nullable = false)
    private GameAnalysisEntity gameAnalysis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_move_id", nullable = false)
    private GameMoveEntity gameMove;

    private Integer legalMovesCount;
    private Integer captureMovesCount;
    private Integer checkMovesCount;
    private Integer promotionMovesCount;

    private Integer depth;
    private Integer selectiveDepth;
    private Long nodes;
    private Long engineTimeMs;

    private Integer bestEvalCp;
    private Integer bestMateIn;

    private Integer playedMoveEvalCp;
    private Integer playedMoveMateIn;

    private Integer lossCp;
}
