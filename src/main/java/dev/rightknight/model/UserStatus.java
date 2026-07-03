package dev.rightknight.model;

/**
 * Enumeration describing the status of an application user.
 *
 * <p>
 * A user may be active (able to login and use the service), disabled
 * (temporarily prevented from logging in by an administrator) or
 * deleted (soft‑deleted; the record remains in the database but the
 * user may no longer access the system). The status field allows
 * Spring Security to prevent authentication for disabled or deleted
 * accounts without permanently removing the user record.
 */
public enum UserStatus {
    /** User is active and may access the service. */
    ACTIVE,
    /** User has been disabled by an administrator. */
    DISABLED,
    /** Soft‑deleted user. */
    DELETED;
}