package dev.rightknight.model; // Проверь свой путь пакета

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Setter
@Getter
@Table(name = "games")
public class GameEntity {
    @Id
    private String id; // Lichess Game ID
    private String userId;
    private String opponentId;
    private int userRating;
    private int opponentRating;
    private float score;
    private String mode;
    private boolean white;
    private boolean rated;
    private ZonedDateTime createdAt;

    @Column(columnDefinition = "TEXT") // PGN бывает длинным, обычного String может не хватить
    private String pgn;
    private String openingName;
    private String openingEco;
    private String clockLimit;

}


