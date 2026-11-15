package com.example.qsale.availability.application;

import com.example.qsale.availability.domain.DateRecommendationService;
import com.example.qsale.availability.dto.DateRecommendationDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/plans")
public class DateRecommendationController {

    private final DateRecommendationService service;

    @GetMapping("/{planId}/date-recommendations")
    public List<DateRecommendationDto> recommend(@PathVariable Long planId) {
        return service.recommend(planId);
    }
}
