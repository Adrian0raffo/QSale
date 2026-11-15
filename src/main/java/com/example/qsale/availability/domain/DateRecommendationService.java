package com.example.qsale.availability.domain;

import com.example.qsale.availability.dto.DateRecommendationDto;
import com.example.qsale.availability.infrastructure.AvailabilityRepository;
import com.example.qsale.participant.domain.ParticipationStatus;
import com.example.qsale.participant.infrastructure.PlanParticipantRepository;
import com.example.qsale.plan.domain.PlanAccessService;
import com.example.qsale.user.domain.UserService;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DateRecommendationService {

    private final AvailabilityRepository availability;
    private final PlanParticipantRepository participants;
    private final PlanAccessService access;
    private final UserService users;

    @Value("${plans.time-zone:America/Lima}")
    private String timeZone = "America/Lima";

    @PreAuthorize("@planAuthorization.isMember(#planId, authentication)")
    @Transactional(readOnly = true)
    public List<DateRecommendationDto> recommend(Long planId) {
        access.getPlanAsMember(planId, users.getCurrentUser());
        Set<Long> members = participants
            .findByPlanId(planId)
            .stream()
            .filter(member -> member.getStatus() == ParticipationStatus.JOINED)
            .map(member -> member.getUser().getId())
            .collect(Collectors.toSet());
        LocalDate today = LocalDate.now(ZoneId.of(timeZone));
        List<Availability> entries = availability
            .findByPlanId(planId)
            .stream()
            .filter(
                entry ->
                    members.contains(entry.getUser().getId()) && !entry.getAvailableDate().isBefore(today)
            )
            .filter(
                entry ->
                    (entry.getStartTime() == null && entry.getEndTime() == null) ||
                    (entry.getStartTime() != null &&
                        entry.getEndTime() != null &&
                        entry.getEndTime().isAfter(entry.getStartTime()))
            )
            .toList();
        long known = entries
            .stream()
            .map(entry -> entry.getUser().getId())
            .distinct()
            .count();
        Map<LocalDate, List<Availability>> days = entries
            .stream()
            .collect(Collectors.groupingBy(Availability::getAvailableDate));
        return days
            .entrySet()
            .stream()
            .map(day -> candidate(day.getKey(), day.getValue(), members.size(), members.size() - known))
            .sorted(
                Comparator.comparingLong(DateRecommendationDto::availableParticipants)
                    .reversed()
                    .thenComparing(dto -> !dto.timeCompatible())
                    .thenComparing(DateRecommendationDto::date)
            )
            .limit(5)
            .toList();
    }

    private DateRecommendationDto candidate(
        LocalDate day,
        List<Availability> entries,
        int total,
        long unknown
    ) {
        Map<Long, List<Availability>> byUser = entries
            .stream()
            .collect(Collectors.groupingBy(entry -> entry.getUser().getId()));
        List<Window> common = new ArrayList<>(List.of(new Window(0, 1440)));
        for (List<Availability> user : byUser.values()) {
            List<Window> next = new ArrayList<>();
            for (Window current : common)
                for (Availability entry : user) {
                    int start = entry.getStartTime() == null ? 0 : minutes(entry.getStartTime());
                    int end = entry.getEndTime() == null ? 1440 : minutes(entry.getEndTime());
                    int left = Math.max(current.start(), start),
                        right = Math.min(current.end(), end);
                    if (left < right) next.add(new Window(left, right));
                }
            common = merge(next);
            if (common.isEmpty()) break;
        }
        Window best = common
            .stream()
            .max(
                Comparator.<Window>comparingInt(window -> window.end() - window.start()).thenComparing(
                    window -> -window.start()
                )
            )
            .orElse(null);
        LocalTime start =
            best == null || (best.start() == 0 && best.end() == 1440)
                ? null
                : LocalTime.of(best.start() / 60, best.start() % 60);
        LocalTime end =
            best == null || best.end() == 1440 ? null : LocalTime.of(best.end() / 60, best.end() % 60);
        return new DateRecommendationDto(
            day,
            byUser.size(),
            total,
            unknown,
            byUser.size() == total,
            best != null,
            start,
            end
        );
    }

    private List<Window> merge(List<Window> windows) {
        List<Window> merged = new ArrayList<>();
        for (Window window : windows.stream().sorted(Comparator.comparingInt(Window::start)).toList()) {
            if (merged.isEmpty() || merged.getLast().end() < window.start()) merged.add(window);
            else {
                Window previous = merged.removeLast();
                merged.add(new Window(previous.start(), Math.max(previous.end(), window.end())));
            }
        }
        return merged;
    }

    private int minutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private record Window(int start, int end) {}
}
