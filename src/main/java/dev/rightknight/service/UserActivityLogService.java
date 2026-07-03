package dev.rightknight.service;

import dev.rightknight.model.AppUserEntity;
import dev.rightknight.model.UserActivityEventType;
import dev.rightknight.model.UserActivityLogEntity;
import dev.rightknight.repository.UserActivityLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for creating entries in the {@code user_activity_log} table. The
 * service centralises event logging and JSON serialisation so other
 * components do not need to interact with the repository directly.
 */
@Service
public class UserActivityLogService {
    private final UserActivityLogRepository repository;
    private final ObjectMapper objectMapper;

    public UserActivityLogService(UserActivityLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Logs an event without request context or additional details.
     *
     * @param user the user associated with the event (may be null)
     * @param eventType the type of event
     */
    @Transactional
    public void log(AppUserEntity user, UserActivityEventType eventType) {
        log(user, eventType, null, null);
    }

    /**
     * Logs an event with HTTP request context and optional extra details.
     *
     * @param user the user associated with the event (may be null)
     * @param eventType the type of event
     * @param request the HTTP request that generated the event; may be {@code null}
     * @param extraDetails additional key/value pairs to include in the details JSON; may be {@code null}
     */
    @Transactional
    public void log(AppUserEntity user, UserActivityEventType eventType, HttpServletRequest request, Map<String, Object> extraDetails) {
        UserActivityLogEntity logEntry = new UserActivityLogEntity();
        logEntry.setUser(user);
        logEntry.setEventType(eventType);
        if (request != null) {
            logEntry.setMethod(request.getMethod());
            logEntry.setPath(request.getRequestURI());
            logEntry.setIpAddress(request.getRemoteAddr());
            logEntry.setUserAgent(request.getHeader("User-Agent"));
        }
        // Combine extra details into JSON string
        if (extraDetails != null && !extraDetails.isEmpty()) {
            try {
                logEntry.setDetails(objectMapper.writeValueAsString(extraDetails));
            } catch (JsonProcessingException e) {
                // On failure, fallback to a simple map with a parsing error message
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("error", "failed to serialise details: " + e.getMessage());
                try {
                    logEntry.setDetails(objectMapper.writeValueAsString(errorMap));
                } catch (JsonProcessingException ex) {
                    // Last resort: set details to null
                    logEntry.setDetails(null);
                }
            }
        }
        repository.save(logEntry);
    }
}