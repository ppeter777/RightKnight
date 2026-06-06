package dev.rightknight.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@Table(name = "game_import_ranges")
public class GameImportRangeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUserEntity appUser;

    @Column(name = "lichess_username", nullable = false, length = 50)
    private String lichessUsername;

    @Column(name = "range_from", nullable = false)
    private ZonedDateTime rangeFrom;

    @Column(name = "range_until", nullable = false)
    private ZonedDateTime rangeUntil;

    @Column(nullable = false, length = 30)
    private String status = "SUCCESS";

    @Column(name = "games_imported", nullable = false)
    private int gamesImported;

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
