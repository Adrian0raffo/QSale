package com.example.qsale.option.dto;

import com.example.qsale.location.dto.LocationDto;
import com.example.qsale.location.domain.TravelEstimateSource;
import com.example.qsale.location.domain.TravelEstimateStatus;
import com.example.qsale.option.domain.OptionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class OptionResponseDto {
    private Long id;
    private OptionType type;
    private String label;
    private String description;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private LocationDto location;
    private Double avgDistanceKm;
    private Integer avgTravelMinutes;
    private boolean selected;
    private String proposedByName;
    private long voteCount;
    private int score;
    private boolean hasVoted;
    private TravelEstimateSource estimateSource;
    private TravelEstimateStatus estimateStatus;
    private Instant estimateUpdatedAt;
}
