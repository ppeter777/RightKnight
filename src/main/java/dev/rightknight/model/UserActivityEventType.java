package dev.rightknight.model;

/**
 * Enumerates the types of activity events that may be logged in
 * the {@code user_activity_log} table. Events are captured by
 * {@link dev.rightknight.service.UserActivityLogService} and
 * administrators can view recent activity in the admin dashboard.
 */
public enum UserActivityEventType {
    USER_LOGIN,
    USER_LOGOUT,
    USER_REGISTERED,
    GAMES_IMPORT_STARTED,
    GAMES_IMPORT_FINISHED,
    GAMES_IMPORT_FAILED,
    GAME_VIEWED,
    ANALYSIS_REQUESTED,
    ADMIN_USER_DISABLED,
    ADMIN_USER_ENABLED,
    ADMIN_USER_GAMES_CLEARED,
    ADMIN_USER_DELETED,
    ADMIN_ROLE_GRANTED,
    ADMIN_ROLE_REVOKED;
}