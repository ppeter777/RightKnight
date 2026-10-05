package dev.rightknight.model;

import dev.rightknight.engine.ScoreBound;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Scores use the side to move in the parent move's fenBefore. */
@Entity
@Table(name = "game_move_analysis_candidate", uniqueConstraints = {
        @UniqueConstraint(name = "uk_move_candidate_rank", columnNames = {"game_move_analysis_id", "pv_rank"}),
        @UniqueConstraint(name = "uk_move_candidate_move", columnNames = {"game_move_analysis_id", "move_uci"})
})
@Getter
@Setter
public class GameMoveAnalysisCandidateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_move_analysis_id", nullable = false)
    private GameMoveAnalysisEntity gameMoveAnalysis;

    @Column(nullable = false)
    private Integer pvRank;
    @Column(nullable = false, length = 5)
    private String moveUci;
    private Integer evalCp;
    private Integer mateIn;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private ScoreBound scoreBound;
    @Column(nullable = false)
    private Integer depth;
    private Integer selectiveDepth;
    @Column(nullable = false, columnDefinition = "text")
    private String pvUci;
}
