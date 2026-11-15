package com.example.qsale.reschedule.dto;

import com.example.qsale.location.dto.LocationDto;
import com.example.qsale.reschedule.domain.RescheduleStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;

public final class RescheduleDtos {

    private RescheduleDtos() {}

    public record Request(
        @FutureOrPresent LocalDate date,
        LocalTime time,
        Long placeOptionId,
        @Size(min = 3, max = 120) String placeLabel,
        @Valid LocationDto location,
        @Size(max = 500) String reason,
        @Future Instant expiresAt
    ) {}

    public record VoteRequest(@NotNull Boolean accepted) {}

    public record Response(
        Long id,
        Long planId,
        Long createdById,
        String createdByName,
        int baseAgreementVersion,
        LocalDate date,
        LocalTime time,
        LocalDate endDate,
        LocalTime endTime,
        Long placeOptionId,
        String placeLabel,
        LocationDto location,
        String reason,
        RescheduleStatus status,
        long yesVotes,
        long noVotes,
        long totalVoters,
        long requiredVotes,
        Boolean myVote,
        boolean canApply,
        boolean canWithdraw,
        Instant createdAt,
        Instant expiresAt
    ) {}
}
