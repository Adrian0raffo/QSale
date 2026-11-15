package com.example.qsale.notification.dto;

import java.util.List;

public record NotificationPageResponseDto(List<NotificationResponseDto> content, int page, int size,
                                          long totalElements, int totalPages, long unreadCount) {
}
