package com.example.qsale.jobs;

import java.util.Map;

public record JobPayload(Long targetId, Long notificationId, String email, String subject,
                         String template, Map<String,Object> variables) {
    public static JobPayload target(Long id) { return new JobPayload(id,null,null,null,null,null); }
}
