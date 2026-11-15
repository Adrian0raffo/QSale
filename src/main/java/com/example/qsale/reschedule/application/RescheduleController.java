package com.example.qsale.reschedule.application;

import com.example.qsale.reschedule.domain.RescheduleService;
import com.example.qsale.reschedule.dto.RescheduleDtos;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/plans/{planId}/reschedules")
@RequiredArgsConstructor
public class RescheduleController {

    private final RescheduleService service;

    @GetMapping
    public List<RescheduleDtos.Response> list(@PathVariable Long planId) {
        return service.list(planId);
    }

    @PostMapping
    public ResponseEntity<RescheduleDtos.Response> create(
        @PathVariable Long planId,
        @Valid @RequestBody RescheduleDtos.Request request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(planId, request));
    }

    @PutMapping("/{id}/vote")
    public RescheduleDtos.Response vote(
        @PathVariable Long planId,
        @PathVariable Long id,
        @Valid @RequestBody RescheduleDtos.VoteRequest request
    ) {
        return service.vote(planId, id, request.accepted());
    }

    @PatchMapping("/{id}/apply")
    public RescheduleDtos.Response apply(@PathVariable Long planId, @PathVariable Long id) {
        return service.apply(planId, id);
    }

    @PatchMapping("/{id}/withdraw")
    public RescheduleDtos.Response withdraw(@PathVariable Long planId, @PathVariable Long id) {
        return service.withdraw(planId, id);
    }
}
