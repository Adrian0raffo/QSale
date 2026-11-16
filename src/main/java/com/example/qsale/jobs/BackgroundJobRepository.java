package com.example.qsale.jobs;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BackgroundJobRepository extends JpaRepository<BackgroundJob,String> {
    @Query(value="""
        SELECT * FROM background_jobs WHERE scope = :scope
        AND ((status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP)
          OR (status = 'RUNNING' AND lease_until < CURRENT_TIMESTAMP))
        ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
        """, nativeQuery=true)
    Optional<BackgroundJob> claimNext(@Param("scope") String scope);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from BackgroundJob job where job.id=:id")
    Optional<BackgroundJob> findForUpdate(@Param("id") String id);

    List<BackgroundJob> findTop100ByScopeAndStatusOrderByCreatedAtAsc(String scope,String status);
    void deleteByStatusAndCompletedAtBefore(String status,Instant before);
}
