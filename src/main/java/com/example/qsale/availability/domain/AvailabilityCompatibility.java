package com.example.qsale.availability.domain;

import com.example.qsale.option.domain.PlanOption;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public final class AvailabilityCompatibility {
    private static final int END_OF_DAY = 86400;
    private AvailabilityCompatibility() {}

    public static long count(List<Availability> entries, PlanOption choice) {
        if (choice == null || choice.getEffectiveDate() == null) return 0;
        LocalDate first = choice.getEffectiveDate();
        LocalDate last = choice.getEffectiveEndDate() == null ? first : choice.getEffectiveEndDate();
        Map<Long, List<Availability>> users = entries.stream()
                .filter(entry -> entry.getAvailableDate() != null && entry.getUser() != null)
                .filter(entry -> !entry.getAvailableDate().isBefore(first) && !entry.getAvailableDate().isAfter(last))
                .collect(Collectors.groupingBy(entry -> entry.getUser().getId()));
        if (first.equals(last) && choice.getStartsAt() == null) return largestCommonGroup(users);
        return users.values().stream().filter(user -> coversRange(user, choice, first, last)).count();
    }

    private static boolean coversRange(List<Availability> entries, PlanOption choice, LocalDate first, LocalDate last) {
        Map<LocalDate, List<Availability>> days = entries.stream().collect(Collectors.groupingBy(Availability::getAvailableDate));
        if (days.size() != ChronoUnit.DAYS.between(first, last) + 1) return false;
        return days.entrySet().stream().allMatch(day -> {
            int start = day.getKey().equals(first) && choice.getStartsAt() != null
                    ? choice.getStartsAt().toLocalTime().toSecondOfDay() : 0;
            int end = day.getKey().equals(last) && choice.getEndsAt() != null
                    ? choice.getEndsAt().toLocalTime().toSecondOfDay()
                    : first.equals(last) && choice.getStartsAt() != null ? start + 1 : END_OF_DAY;
            return merge(day.getValue()).stream().anyMatch(window -> window.start() <= start && window.end() >= end);
        });
    }

    private static long largestCommonGroup(Map<Long, List<Availability>> users) {
        TreeMap<Integer, Integer> changes = new TreeMap<>();
        users.values().forEach(entries -> merge(entries).forEach(window -> {
            changes.merge(window.start(), 1, Integer::sum);
            changes.merge(window.end(), -1, Integer::sum);
        }));
        int active = 0, maximum = 0;
        for (int change : changes.values()) {
            active += change;
            maximum = Math.max(maximum, active);
        }
        return maximum;
    }

    private static List<Window> merge(List<Availability> entries) {
        List<Window> windows = entries.stream()
                .filter(entry -> (entry.getStartTime() == null) == (entry.getEndTime() == null))
                .map(entry -> new Window(entry.getStartTime() == null ? 0 : entry.getStartTime().toSecondOfDay(),
                        entry.getEndTime() == null ? END_OF_DAY : entry.getEndTime().toSecondOfDay()))
                .filter(window -> window.start() < window.end())
                .sorted(Comparator.comparingInt(Window::start)).toList();
        List<Window> merged = new ArrayList<>();
        for (Window window : windows) {
            if (merged.isEmpty() || merged.getLast().end() < window.start()) merged.add(window);
            else {
                Window previous = merged.removeLast();
                merged.add(new Window(previous.start(), Math.max(previous.end(), window.end())));
            }
        }
        return merged;
    }

    private record Window(int start, int end) {}
}
