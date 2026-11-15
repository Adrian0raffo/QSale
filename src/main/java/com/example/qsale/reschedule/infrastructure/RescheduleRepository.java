package com.example.qsale.reschedule.infrastructure;

import com.example.qsale.reschedule.domain.RescheduleProposal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RescheduleRepository extends JpaRepository<RescheduleProposal, Long> {
    List<RescheduleProposal> findByPlanIdOrderByIdDesc(Long planId);
}
