package dev.rightknight.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@Table(name = "game_sync_state")
public class GameSyncStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUserEntity appUser;

    @Column(name = "lichess_username", nullable = false, length = 50)
    private String lichessUsername;

    @Column(name = "sync_from", nullable = false)
    private ZonedDateTime syncFrom;

    @Column(name = "last_success_at")
    private ZonedDateTime lastSuccessAt;

    @Column(name = "newest_game_created_at")
    private ZonedDateTime newestGameCreatedAt;

    @Column(name = "newest_game_id")
    private String newestGameId;

    @Column(nullable = false, length = 30)
    private String status = "IDLE";

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    void prePersist() {
        ZonedDateTime now = ZonedDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}
