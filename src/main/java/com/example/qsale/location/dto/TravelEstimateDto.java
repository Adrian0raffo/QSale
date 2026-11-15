package com.example.qsale.location.dto;

import com.example.qsale.location.domain.TravelEstimateSource;

public record TravelEstimateDto(double avgDistanceKm, int avgTravelMinutes, TravelEstimateSource source) {
}
