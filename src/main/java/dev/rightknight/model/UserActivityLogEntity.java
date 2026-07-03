package dev.rightknight.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code user_activity_log} table.
 *
 * <p>This table stores audit information about user and administrator
 * actions within the application. Each log entry captures the
 * associated user (if any), the event type, request details and
 * an optional JSON payload stored in the {@code details} column.</p>
 */
@Entity
@Table(name = "user_activity_log")
public class UserActivityLogEntity {

    /** Primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user related to this event (nullable). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUserEntity user;

    /** The type of event that occurred. */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 64, nullable = false)
    private UserActivityEventType eventType;

    /** HTTP method of the request that triggered the event, if any. */
    @Column(name = "method", length = 16)
    private String method;

    /** Path of the request that triggered the event. */
    @Column(name = "path", length = 512)
    private String path;

    /** Response status code associated with the request. */
    @Column(name = "status_code")
    private Integer statusCode;

    /** IP address of the client that triggered the event. */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    /** User agent string of the client. */
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /**
     * Arbitrary JSON payload with additional details about the event.
     * The column uses the {@code jsonb} type in PostgreSQL. Hibernate
     * maps it using {@link SqlTypes#JSON}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details")
    private String details;

    /** Timestamp when the event was created. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AppUserEntity getUser() {
        return user;
    }

    public void setUser(AppUserEntity user) {
        this.user = user;
    }

    public UserActivityEventType getEventType() {
        return eventType;
    }

    public void setEventType(UserActivityEventType eventType) {
        this.eventType = eventType;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}