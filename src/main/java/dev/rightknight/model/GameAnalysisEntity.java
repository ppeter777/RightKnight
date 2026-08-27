package dev.rightknight.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "game_analysis")
@Getter
@Setter
public class GameAnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private GameEntity game;

    private String engineName;
    private String engineVersion;

    private Integer requestedDepth;
    private Integer multiPv;

//    @Enumerated(EnumType.STRING)
//    private AnalysisStatus status;

    private ZonedDateTime createdAt;
    private ZonedDateTime completedAt;
}
