package dev.rightknight.service.admin;

import dev.rightknight.model.*;
import dev.rightknight.repository.AppUserRepository;
import dev.rightknight.repository.GameRepository;
import dev.rightknight.service.UserActivityLogService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Service that provides user‑specific information for the admin UI and
 * operations for managing users. It uses repositories to aggregate
 * statistics about users and logs administrative actions.
 */
@Service
public class AdminUserService {
    private final AppUserRepository userRepository;
    private final GameRepository gameRepository;
    private final UserActivityLogService activityLogService;

    public AdminUserService(AppUserRepository userRepository, GameRepository gameRepository,
                            UserActivityLogService activityLogService) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.activityLogService = activityLogService;
    }

    /** Returns the user by id or throws an {@link IllegalArgumentException}. */
    public AppUserEntity requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    /**
     * DTO summarising user information for the list view.
     */
    public static class UserSummary {
        public Long id;
        public String username;
        public String email;
        public String lichessUsername;
        public String role;
        public String status;
        public java.time.OffsetDateTime createdAt;
        public java.time.OffsetDateTime lastActivityAt;
        public long gamesCount;
        public java.time.ZonedDateTime firstGameAt;
        public java.time.ZonedDateTime lastGameAt;
    }

    /**
     * Builds a summary DTO for a given user.
     *
     * @param user the user entity
     * @return summary information
     */
    public UserSummary buildSummary(AppUserEntity user) {
        UserSummary summary = new UserSummary();
        summary.id = user.getId();
        summary.username = user.getUsername();
        summary.email = user.getEmail();
        summary.lichessUsername = user.getLichessUsername();
        summary.role = user.getRole();
        summary.status = user.getStatus();
        summary.createdAt = user.getCreatedAt();
        summary.lastActivityAt = user.getUpdatedAt();
        summary.gamesCount = gameRepository.countByOwner(user);
        // Use earliest and latest game creation timestamps, if available
        summary.firstGameAt =
                gameRepository.findFirstByOwnerOrderByCreatedAtAsc(user)
                        .map(GameEntity::getCreatedAt)
                        .orElse(null);

        summary.lastGameAt =
                gameRepository.findFirstByOwnerOrderByCreatedAtDesc(user)
                        .map(GameEntity::getCreatedAt)
                        .orElse(null);
        return summary;
    }

    /**
     * Returns a list of all users summarised for display. Filters may be
     * applied by username/email, Lichess username, status and role. If no
     * filter value is provided for a field, that field is ignored.
     */
    public List<UserSummary> findUsers(String usernameOrEmailFilter,
                                       String lichessUsernameFilter,
                                       UserStatus statusFilter,
                                       UserRole roleFilter) {
        // Fetch all users first (administrative pages are rarely huge). In
        // a large system you would add custom queries to filter in the
        // database. Here we filter in memory for simplicity and minimal
        // changes to existing repositories.
        List<AppUserEntity> allUsers = StreamSupport.stream(userRepository.findAll().spliterator(), false)
                .toList();
        return allUsers.stream()
                .filter(u -> {
                    boolean matches = true;
                    if (usernameOrEmailFilter != null && !usernameOrEmailFilter.isBlank()) {
                        String filter = usernameOrEmailFilter.toLowerCase();
                        boolean usernameMatches = u.getUsername() != null && u.getUsername().toLowerCase().contains(filter);
                        boolean emailMatches = u.getEmail() != null && u.getEmail().toLowerCase().contains(filter);
                        matches &= (usernameMatches || emailMatches);
                    }
                    if (lichessUsernameFilter != null && !lichessUsernameFilter.isBlank()) {
                        matches &= (u.getLichessUsername() != null && u.getLichessUsername().toLowerCase().contains(lichessUsernameFilter.toLowerCase()));
                    }
                    if (statusFilter != null) {
                        matches &= statusFilter.name().equals(u.getStatus());
                    }
                    if (roleFilter != null) {
                        matches &= roleFilter.name().equals(u.getRole());
                    }
                    return matches;
                })
                .map(this::buildSummary)
                .collect(Collectors.toList());
    }

    /**
     * Disables a user. Sets status to DISABLED and persists. Also logs the action.
     *
     * @param user the user to disable
     */
    @Transactional
    public void disableUser(AppUserEntity user) {
        user.setStatus(UserStatus.DISABLED.name());
        userRepository.save(user);
        activityLogService.log(user, UserActivityEventType.ADMIN_USER_DISABLED);
    }

    /**
     * Enables a user. Sets status to ACTIVE and persists. Also logs the action.
     *
     * @param user the user to enable
     */
    @Transactional
    public void enableUser(AppUserEntity user) {
        user.setStatus(UserStatus.ACTIVE.name());
        userRepository.save(user);
        activityLogService.log(user, UserActivityEventType.ADMIN_USER_ENABLED);
    }

    /**
     * Soft deletes a user by setting status to DELETED. Also logs the action.
     *
     * @param user the user to delete
     */
    @Transactional
    public void deleteUser(AppUserEntity user) {
        user.setStatus(UserStatus.DELETED.name());
        userRepository.save(user);
        activityLogService.log(user, UserActivityEventType.ADMIN_USER_DELETED);
    }

    /**
     * Clears all games for a user. Deletes games and optionally associated
     * moves; call appropriate repository methods. Also logs the action.
     *
     * @param user the user whose games should be cleared
     */
    @Transactional
    public void clearUserGames(AppUserEntity user) {
        // The GameRepository should cascade delete associated moves via
        // database foreign keys or custom query methods. For a minimal
        // implementation we assume a deleteByOwner method exists.
        gameRepository.deleteByOwner(user);
        activityLogService.log(user, UserActivityEventType.ADMIN_USER_GAMES_CLEARED);
    }

    /**
     * Grants administrative privileges to a user.
     *
     * @param user the user to promote
     */
    @Transactional
    public void grantAdminRole(AppUserEntity user) {
        user.setRole(UserRole.ADMIN.name());
        userRepository.save(user);
        activityLogService.log(user, UserActivityEventType.ADMIN_ROLE_GRANTED);
    }

    /**
     * Revokes administrative privileges from a user (downgrades to USER).
     *
     * @param user the user to demote
     */
    @Transactional
    public void revokeAdminRole(AppUserEntity user) {
        user.setRole(UserRole.USER.name());
        userRepository.save(user);
        activityLogService.log(user, UserActivityEventType.ADMIN_ROLE_REVOKED);
    }
}