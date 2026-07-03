package dev.rightknight.service.admin;

import dev.rightknight.model.UserActivityLogEntity;
import dev.rightknight.repository.UserActivityLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AdminActivityService {

    private final UserActivityLogRepository activityRepository;

    public AdminActivityService(
            UserActivityLogRepository activityRepository
    ) {
        this.activityRepository = activityRepository;
    }

    public List<UserActivityLogEntity> getRecentActivities(int limit) {
        return activityRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(0, limit)
        );
    }
}