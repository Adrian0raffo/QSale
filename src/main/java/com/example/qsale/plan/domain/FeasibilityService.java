package com.example.qsale.plan.domain;

import com.example.qsale.availability.infrastructure.AvailabilityRepository;
import com.example.qsale.availability.domain.AvailabilityCompatibility;
import java.util.Comparator;
import com.example.qsale.commitment.domain.CommitmentStatus;
import com.example.qsale.commitment.infrastructure.CommitmentResponseRepository;
import com.example.qsale.option.domain.OptionType;
import com.example.qsale.option.domain.PlanOption;
import com.example.qsale.option.infrastructure.PlanOptionRepository;
import com.example.qsale.participant.infrastructure.PlanParticipantRepository;
import com.example.qsale.participant.domain.ParticipationStatus;
import com.example.qsale.plan.dto.FeasibilityResponseDto;
import com.example.qsale.user.domain.UserService;
import com.example.qsale.vote.infrastructure.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
public class FeasibilityService {

    @Value("${plans.time-zone:America/Lima}")
    private String timeZone = "America/Lima";

    private static final int READY_THRESHOLD = 75;
    private static final int TO_BE_DEFINED_THRESHOLD = 40;

    private final PlanAccessService planAccessService;
    private final UserService userService;
    private final PlanParticipantRepository participantRepository;
    private final CommitmentResponseRepository commitmentRepository;
    private final AvailabilityRepository availabilityRepository;
    private final PlanOptionRepository optionRepository;
    private final VoteRepository voteRepository;

    @PreAuthorize("@planAuthorization.isMember(#planId, authentication)")
    @Transactional(readOnly = true)
    public FeasibilityResponseDto getFeasibility(Long planId) {
        Plan plan = planAccessService.getPlanAsMember(planId, userService.getCurrentUser());
        long confirmed = countConfirmed(planId);
        int score = calculateScore(plan, confirmed);
        boolean day = hasVotedChoice(planId, OptionType.DATE), place = hasVotedChoice(planId, OptionType.PLACE);
        return new FeasibilityResponseDto(plan.getId(), score, resolveStatus(score, confirmed, plan.getMinParticipants(), day, place),
                confirmed, plan.getMinParticipants(),
                participantRepository.countByPlanIdAndStatus(planId, ParticipationStatus.JOINED), day, place);
    }

    @Transactional
    public boolean recalculate(Long planId) {
        Plan plan = planAccessService.findPlanForUpdate(planId).orElse(null);
        if (plan == null || !plan.isOpen()) {
            return false;
        }
        FeasibilityStatus previous = plan.getFeasibilityStatus();
        updateFeasibility(plan);
        return previous != FeasibilityStatus.READY_TO_CLOSE
                && plan.getFeasibilityStatus() == FeasibilityStatus.READY_TO_CLOSE;
    }

    @Transactional
    public void updateFeasibility(Plan plan) {
        long confirmed = countConfirmed(plan.getId());
        int score = calculateScore(plan, confirmed);
        plan.setFeasibilityScore(score);
        plan.setFeasibilityStatus(resolveStatus(score, confirmed, plan.getMinParticipants(),
                hasVotedChoice(plan.getId(), OptionType.DATE), hasVotedChoice(plan.getId(), OptionType.PLACE)));
    }

    public int calculateOptionScore(PlanOption option, long votes, long participants) {
        double voteRatio = participants == 0 ? 0 : Math.min((double) votes / participants, 1.0);
        double proximity = option.getAvgDistanceKm() == null ? 0 : Math.max(0, 1 - option.getAvgDistanceKm() / 20);
        return (int) Math.round(voteRatio * 80 + proximity * 20);
    }

    private int calculateScore(Plan plan, long confirmed) {
        long participants = Math.max(
                participantRepository.countByPlanIdAndStatus(plan.getId(), ParticipationStatus.JOINED), 1);
        double confirmationRatio = Math.min((double) confirmed / plan.getMinParticipants(), 1.0);
        double agreementRatio = calculateAgreement(plan.getId(), participants);
        double availabilityRatio = Math.min((double) countUsersWithAvailability(plan.getId()) / participants, 1.0);
        return (int) Math.round(confirmationRatio * 50 + agreementRatio * 30 + availabilityRatio * 20);
    }

    private double calculateAgreement(Long planId, long participants) {
        return Arrays.stream(OptionType.values())
                .map(type -> optionRepository.findByPlanIdAndType(planId, type).stream()
                        .filter(option -> type != OptionType.DATE || option.isUpcoming(ZoneId.of(timeZone)))
                        .filter(option -> type != OptionType.PLACE || option.getLocation() != null).toList())
                .filter(options -> !options.isEmpty())
                .mapToDouble(options -> Math.min((double) maxVotes(options) / participants, 1.0))
                .average()
                .orElse(0);
    }

    private long maxVotes(List<PlanOption> options) {
        return options.stream()
                .mapToLong(option -> voteRepository.countByOptionId(option.getId()))
                .max()
                .orElse(0);
    }

    private long countUsersWithAvailability(Long planId) {
        List<com.example.qsale.availability.domain.Availability> entries = availabilityRepository.findByPlanId(planId);
        PlanOption date = optionRepository.findByPlanIdAndType(planId, OptionType.DATE).stream()
                .filter(option -> option.isUpcoming(ZoneId.of(timeZone)))
                .filter(option -> voteRepository.countByOptionId(option.getId()) > 0)
                .max(Comparator.<PlanOption>comparingLong(option -> voteRepository.countByOptionId(option.getId()))
                        .thenComparing(option -> -option.getId())).orElse(null);
        return AvailabilityCompatibility.count(entries, date);
    }

    private long countConfirmed(Long planId) {
        return commitmentRepository.countByPlanIdAndStatus(planId, CommitmentStatus.CONFIRMED);
    }

    private boolean hasVotedChoice(Long planId, OptionType type) {
        return optionRepository.findByPlanIdAndType(planId, type).stream()
                .filter(option -> type != OptionType.DATE || option.isUpcoming(ZoneId.of(timeZone)))
                .filter(option -> type != OptionType.PLACE || option.getLocation() != null)
                .anyMatch(option -> voteRepository.countByOptionId(option.getId()) > 0);
    }

    private FeasibilityStatus resolveStatus(int score, long confirmed, int minParticipants, boolean day, boolean place) {
        if (confirmed >= minParticipants && score >= READY_THRESHOLD && day && place) {
            return FeasibilityStatus.READY_TO_CLOSE;
        }
        if (score >= TO_BE_DEFINED_THRESHOLD) {
            return FeasibilityStatus.TO_BE_DEFINED;
        }
        return FeasibilityStatus.NOT_VIABLE;
    }
}
