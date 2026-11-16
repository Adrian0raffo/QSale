package com.example.qsale.availability;

import com.example.qsale.availability.domain.*;
import com.example.qsale.option.domain.PlanOption;
import com.example.qsale.user.domain.User;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AvailabilityCompatibilityTest {
    private final LocalDate day=LocalDate.of(2027,1,20);

    @Test void unrelatedDaysAndDisjointHoursDoNotCountAsEveryoneAvailable() {
        assertEquals(0, AvailabilityCompatibility.count(List.of(entry(1,day.minusDays(1),null,null)),choice(null,null)));
        assertEquals(1, AvailabilityCompatibility.count(List.of(entry(1,day,"09:00","10:00"),entry(2,day,"17:00","18:00")),choice(null,null)));
    }

    @Test void overlappingEntriesFromOnePersonCannotCountTwice() {
        assertEquals(2, AvailabilityCompatibility.count(List.of(entry(1,day,"09:00","12:00"),entry(1,day,"10:00","11:00"),entry(2,day,"10:30","11:30")),choice(null,null)));
    }

    @Test void aKnownHourMustFitInsideTheWholeRegisteredWindow() {
        assertEquals(1, AvailabilityCompatibility.count(List.of(entry(1,day,"09:00","12:00"),entry(2,day,"11:00","12:00")),choice("10:00","11:30")));
        assertEquals(0, AvailabilityCompatibility.count(List.of(entry(1,day,"09:00","10:00")),choice("10:00",null)));
    }

    @Test void allDayEntriesAndAdjacentWindowsCanCoverTheChosenInterval() {
        assertEquals(2, AvailabilityCompatibility.count(List.of(entry(1,day,null,null),entry(2,day,"09:00","10:00"),entry(2,day,"10:00","12:00")),choice("09:30","11:30")));
    }

    @Test void aDateRangeRequiresAvailabilityOnEveryDayWithoutIteratingUnboundedDates() {
        PlanOption range=choice(null,null);range.setEndsOn(day.plusDays(1));
        assertEquals(1, AvailabilityCompatibility.count(List.of(entry(1,day,null,null),entry(1,day.plusDays(1),null,null),entry(2,day,null,null)),range));
        range.setEndsOn(day.plusYears(100));
        assertEquals(0, AvailabilityCompatibility.count(List.of(entry(1,day,null,null)),range));
    }

    private PlanOption choice(String start,String end) {
        PlanOption result=new PlanOption();result.setStartsOn(day);
        if(start!=null)result.setStartsAt(day.atTime(LocalTime.parse(start)));
        if(end!=null)result.setEndsAt(day.atTime(LocalTime.parse(end)));
        return result;
    }
    private Availability entry(long id,LocalDate date,String start,String end) {
        User user=new User();user.setId(id);
        return new Availability(null,user,date,start==null?null:LocalTime.parse(start),end==null?null:LocalTime.parse(end));
    }
}
