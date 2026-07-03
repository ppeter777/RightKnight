package dev.rightknight.model;

/**
 * Enumeration of application user roles.
 *
 * <p>
 * The system currently supports only two roles:
 * regular users and administrators. A user with
 * the {@link #ADMIN} role may access the administrative
 * dashboard and management pages under {@code /admin/**}.
 */
public enum UserRole {
    /** Regular user of the system. */
    USER,
    /** Administrator with elevated privileges. */
    ADMIN;
}