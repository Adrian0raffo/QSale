package com.example.qsale.availability.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record DateRecommendationDto(
    LocalDate date,
    long availableParticipants,
    long totalParticipants,
    long unknownParticipants,
    boolean allParticipantsAvailable,
    boolean timeCompatible,
    LocalTime commonStartTime,
    LocalTime commonEndTime
) {}
