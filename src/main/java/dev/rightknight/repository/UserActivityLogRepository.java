package dev.rightknight.repository;

import dev.rightknight.model.UserActivityLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface UserActivityLogRepository
        extends JpaRepository<UserActivityLogEntity, Long> {

    List<UserActivityLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
        select a
        from UserActivityLogEntity a
        where a.eventType like '%FAILED%'
           or a.eventType like 'ADMIN_%'
        order by a.createdAt desc
    """)
    List<UserActivityLogEntity> findRecentErrors(Pageable pageable);
}