package dev.rightknight.service.admin;

import dev.rightknight.model.UserStatus;
import dev.rightknight.model.UserActivityLogEntity;
import dev.rightknight.repository.AppUserRepository;
import dev.rightknight.repository.GameRepository;
import dev.rightknight.repository.UserActivityLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service providing aggregate metrics for the admin dashboard. It queries
 * underlying repositories to count users and games as well as retrieving
 * recent user activity log entries.
 */
@Service
public class AdminDashboardService {
    private final AppUserRepository userRepository;
    private final GameRepository gameRepository;
    private final UserActivityLogRepository activityRepository;

    public AdminDashboardService(AppUserRepository userRepository,
                                 GameRepository gameRepository,
                                 UserActivityLogRepository activityRepository) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.activityRepository = activityRepository;
    }

    /**
     * Returns the total number of users in the system.
     */
    public long getTotalUsers() {
        return userRepository.count();
    }

    /**
     * Returns the number of active users.
     */
    public long getActiveUsers() {
        return userRepository.countByStatus("ACTIVE");
    }

    /**
     * Returns the total number of games imported.
     */
    public long getTotalGames() {
        return gameRepository.count();
    }

    /**
     * Returns the number of games imported in the last 24 hours.
     */
    public long getGamesLast24h() {
        return gameRepository.countByCreatedAtAfter(ZonedDateTime.now().minusHours(24));
    }

    /**
     * Returns the number of games imported in the last 7 days.
     */
    public long getGamesLast7d() {
        return gameRepository.countByCreatedAtAfter(ZonedDateTime.now().minusDays(7));
    }

    /**
     * Returns the number of games imported in the last 30 days.
     */
    public long getGamesLast30d() {
        return gameRepository.countByCreatedAtAfter(ZonedDateTime.now().minusDays(30));
    }

    /**
     * Returns a list of the most recent user activity events.
     *
     * @param limit maximum number of entries
     * @return a list of recent activity events
     */
    public List<UserActivityLogEntity> getRecentActivities(int limit) {
        return activityRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(0, limit)
        );
    }

    /**
     * Returns a list of the most recent events that represent errors.
     *
     * <p>This implementation simply returns the latest activity entries
     * because at the moment the application does not differentiate
     * between error and non‑error events in the log. In a more
     * sophisticated implementation this method could filter by event
     * type.</p>
     *
     * @param limit maximum number of entries
     * @return a list of recent error events
     */
    public List<UserActivityLogEntity> getRecentErrors(int limit) {
        return activityRepository.findRecentErrors(
                PageRequest.of(0, limit)
        );
    }
}