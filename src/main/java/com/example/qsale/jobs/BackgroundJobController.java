package com.example.qsale.jobs;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/jobs") @RequiredArgsConstructor
public class BackgroundJobController {
    private final BackgroundQueue queue;
    @GetMapping public List<BackgroundQueue.Summary> failed() { return queue.failedJobs(); }
    @PostMapping("/{id}/retry") public ResponseEntity<Void> retry(@PathVariable String id) {
        queue.retry(id);return ResponseEntity.noContent().build();
    }
}
