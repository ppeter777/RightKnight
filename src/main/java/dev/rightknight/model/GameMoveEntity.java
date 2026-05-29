//package dev.rightknight.model;
//
//import jakarta.persistence.*;
//import lombok.Getter;
//import lombok.Setter;
//
//@Entity
//@Table(name = "game_moves")
//@Getter
//@Setter
//public class GameMoveEntity {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    @ManyToOne(optional = false)
//    @JoinColumn(name = "game_id")
//    private GameEntity game;
//
//    private int ply;          // 1, 2, 3...
//    private int moveNumber;   // 1, 1, 2, 2...
//    private boolean whiteMove;
//
//    private String san;       // e4, Nf3, O-O
//    private String uci;       // e2e4
//
//    @Column(columnDefinition = "TEXT")
//    private String fenBefore;
//
//    @Column(columnDefinition = "TEXT")
//    private String fenAfter;
//
//    private Long clockBeforeMs;
//    private Long clockAfterMs;
//    private Long moveTimeMs;
//}
