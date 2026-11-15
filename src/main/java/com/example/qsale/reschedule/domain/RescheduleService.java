package com.example.qsale.reschedule.domain;

import com.example.qsale.commitment.domain.*;
import com.example.qsale.commitment.infrastructure.CommitmentResponseRepository;
import com.example.qsale.exceptions.*;
import com.example.qsale.location.domain.Location;
import com.example.qsale.location.domain.TravelEstimateStatus;
import com.example.qsale.location.dto.LocationDto;
import com.example.qsale.notification.domain.*;
import com.example.qsale.option.domain.*;
import com.example.qsale.option.events.PlaceOptionCreatedEvent;
import com.example.qsale.option.infrastructure.PlanOptionRepository;
import com.example.qsale.participant.domain.*;
import com.example.qsale.participant.infrastructure.PlanParticipantRepository;
import com.example.qsale.plan.domain.*;
import com.example.qsale.reschedule.dto.RescheduleDtos;
import com.example.qsale.reschedule.infrastructure.*;
import com.example.qsale.user.domain.*;
import com.example.qsale.vote.domain.Vote;
import com.example.qsale.vote.infrastructure.VoteRepository;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RescheduleService {

    private final RescheduleRepository proposals;
    private final RescheduleVoteRepository ballots;
    private final PlanAccessService access;
    private final UserService users;
    private final PlanParticipantRepository participants;
    private final PlanOptionRepository options;
    private final CommitmentResponseRepository commitments;
    private final VoteRepository votes;
    private final NotificationService notifications;
    private final ModelMapper mapper;
    private final ApplicationEventPublisher events;

    @Value("${plans.time-zone:America/Lima}")
    private String timeZone = "America/Lima";

    @PreAuthorize("@planAuthorization.isMember(#planId, authentication)")
    @Transactional
    public List<RescheduleDtos.Response> list(Long planId) {
        User user = users.getCurrentUser();
        Plan plan = access.lockAsMember(planId, user);
        Set<Long> electorate = electorate(planId);
        return proposals
            .findByPlanIdOrderByIdDesc(planId)
            .stream()
            .map(proposal -> {
                resolve(proposal, electorate);
                return response(proposal, user, electorate);
            })
            .toList();
    }

    @PreAuthorize("@planAuthorization.isJoined(#planId, authentication)")
    @Transactional
    public RescheduleDtos.Response create(Long planId, RescheduleDtos.Request request) {
        User user = users.getCurrentUser();
        Plan plan = access.lockAsMember(planId, user);
        access.requireJoined(planId, user);
        requireConfirmed(plan);
        PlanOption currentDate = selected(planId, OptionType.DATE);
        PlanOption currentPlace = selected(planId, OptionType.PLACE);
        LocalDate day = request.date() == null ? currentDate.getEffectiveDate() : request.date();
        if (
            day == null || day.isBefore(LocalDate.now(ZoneId.of(timeZone)))
        ) throw new InvalidOperationException("Choose today or a future day");
        if (
            request.time() != null &&
            !day.atTime(request.time()).atZone(ZoneId.of(timeZone)).toInstant().isAfter(Instant.now())
        ) throw new InvalidOperationException("Choose a future time or leave it unspecified");
        if (
            request.location() != null && request.placeOptionId() != null
        ) throw new InvalidOperationException("Choose an existing place or a new location, not both");
        RescheduleProposal proposal = new RescheduleProposal();
        proposal.setPlan(plan);
        proposal.setCreatedBy(user);
        proposal.setBaseAgreementVersion(plan.getAgreementVersion());
        proposal.setProposedDate(day);
        proposal.setProposedTime(request.time());
        preserveDuration(proposal, currentDate);
        proposal.setReason(request.reason());
        if (request.location() != null) {
            if (
                request.placeLabel() == null || request.placeLabel().isBlank()
            ) throw new InvalidOperationException("A new place needs a name");
            proposal.setPlaceLabel(request.placeLabel().strip());
            copyLocation(proposal, request.location());
        } else {
            PlanOption place =
                request.placeOptionId() == null
                    ? currentPlace
                    : options
                          .findByIdAndPlanId(request.placeOptionId(), planId)
                          .filter(
                              option -> option.getType() == OptionType.PLACE && option.getLocation() != null
                          )
                          .orElseThrow(() -> new InvalidOperationException("Choose a place from this plan"));
            proposal.setPlaceOptionId(place.getId());
            proposal.setPlaceLabel(place.getLabel());
            copyLocation(proposal, mapper.map(place.getLocation(), LocationDto.class));
        }
        boolean sameDay = day.equals(currentDate.getEffectiveDate());
        LocalTime currentTime =
            currentDate.getStartsAt() == null ? null : currentDate.getStartsAt().toLocalTime();
        boolean samePlace = Objects.equals(proposal.getPlaceOptionId(), currentPlace.getId());
        if (
            sameDay && Objects.equals(request.time(), currentTime) && samePlace
        ) throw new InvalidOperationException("The proposal must change the day, time or place");
        Instant expiry =
            request.expiresAt() == null
                ? day.plusDays(1).atStartOfDay(ZoneId.of(timeZone)).toInstant()
                : request.expiresAt();
        Instant lastPossibleVote =
            request.time() == null
                ? day.plusDays(1).atStartOfDay(ZoneId.of(timeZone)).toInstant()
                : day.atTime(request.time()).atZone(ZoneId.of(timeZone)).toInstant();
        if (expiry.isAfter(lastPossibleVote)) expiry = lastPossibleVote;
        if (!expiry.isAfter(Instant.now())) throw new InvalidOperationException(
            "The voting deadline must be in the future"
        );
        proposal.setExpiresAt(expiry);
        proposals.saveAndFlush(proposal);
        saveVote(proposal, user, true);
        Set<Long> electorate = electorate(planId);
        resolve(proposal, electorate);
        notifications.notifyJoinedParticipants(
            plan,
            NotificationType.RESCHEDULE_PROPOSED,
            user.getName() +
                " propuso cambiar " +
                plan.getName() +
                " al " +
                day +
                " en " +
                proposal.getPlaceLabel() +
                ". Vota si asistirías con ese cambio; el acuerdo actual se conserva."
        );
        return response(proposal, user, electorate);
    }

    @PreAuthorize("@planAuthorization.isJoined(#planId, authentication)")
    @Transactional
    public RescheduleDtos.Response vote(Long planId, Long proposalId, boolean accepted) {
        User user = users.getCurrentUser();
        access.lockAsMember(planId, user);
        access.requireJoined(planId, user);
        RescheduleProposal proposal = proposal(planId, proposalId);
        Set<Long> electorate = electorate(planId);
        resolve(proposal, electorate);
        requireActive(proposal);
        saveVote(proposal, user, accepted);
        resolve(proposal, electorate);
        return response(proposal, user, electorate);
    }

    @PreAuthorize("@planAuthorization.isOrganizer(#planId, authentication)")
    @Transactional
    public RescheduleDtos.Response apply(Long planId, Long proposalId) {
        User user = users.getCurrentUser();
        Plan plan = access.lockAsOrganizer(planId, user);
        RescheduleProposal proposal = proposal(planId, proposalId);
        Set<Long> electorate = electorate(planId);
        resolve(proposal, electorate);
        if (proposal.getStatus() != RescheduleStatus.APPROVED) throw new InvalidOperationException(
            "A majority and the minimum number of attendees must support this proposal"
        );
        if (
            proposal.getProposedDate().isBefore(LocalDate.now(ZoneId.of(timeZone)))
        ) throw new InvalidOperationException("The proposed day has already passed");
        options.findByPlanId(planId).forEach(option -> option.setSelected(false));
        PlanOption day = new PlanOption();
        day.setPlan(plan);
        day.setProposedBy(proposal.getCreatedBy());
        day.setType(OptionType.DATE);
        day.setLabel("Nueva fecha: " + proposal.getProposedDate());
        day.setStartsOn(proposal.getProposedDate());
        day.setEndsOn(proposal.getProposedEndDate());
        if (proposal.getProposedEndDate() != null && proposal.getProposedEndTime() != null) day.setEndsAt(
            proposal.getProposedEndDate().atTime(proposal.getProposedEndTime())
        );
        if (proposal.getProposedTime() != null) day.setStartsAt(
            proposal.getProposedDate().atTime(proposal.getProposedTime())
        );
        day.setSelected(true);
        plan.addOption(day);
        options.saveAndFlush(day);
        PlanOption place =
            proposal.getPlaceOptionId() == null
                ? null
                : options.findByIdAndPlanId(proposal.getPlaceOptionId(), planId).orElse(null);
        if (place == null) {
            place = new PlanOption();
            place.setPlan(plan);
            place.setProposedBy(proposal.getCreatedBy());
            place.setType(OptionType.PLACE);
            place.setLabel(proposal.getPlaceLabel());
            place.setLocation(mapper.map(location(proposal), Location.class));
            place.setEstimateStatus(TravelEstimateStatus.PENDING);
            plan.addOption(place);
            options.saveAndFlush(place);
            events.publishEvent(new PlaceOptionCreatedEvent(this, place.getId()));
        }
        place.setSelected(true);
        Map<Long, RescheduleVote> choices = ballots
            .findByProposalId(proposalId)
            .stream()
            .collect(Collectors.toMap(ballot -> ballot.getUser().getId(), Function.identity()));
        for (PlanParticipant member : participants.findByPlanId(planId)) {
            if (member.getStatus() != ParticipationStatus.JOINED) continue;
            RescheduleVote choice = choices.get(member.getUser().getId());
            CommitmentStatus status =
                choice == null
                    ? CommitmentStatus.INTERESTED
                    : choice.isAccepted()
                      ? CommitmentStatus.CONFIRMED
                      : CommitmentStatus.CANT_GO;
            CommitmentResponse commitment = commitments
                .findByPlanIdAndUserId(planId, member.getUser().getId())
                .orElseGet(() -> new CommitmentResponse(plan, member.getUser(), status));
            commitment.setStatus(status);
            commitment.setRespondedAt(LocalDateTime.now());
            commitments.save(commitment);
            if (status == CommitmentStatus.CONFIRMED) {
                votes.save(new Vote(day, member.getUser()));
                if (!votes.existsByOptionIdAndUserId(place.getId(), member.getUser().getId())) votes.save(
                    new Vote(place, member.getUser())
                );
            }
        }
        plan.setTentativeStartDate(proposal.getProposedDate());
        plan.setTentativeEndDate(proposal.getProposedEndDate());
        plan.setAgreementVersion(plan.getAgreementVersion() + 1);
        plan.setClosedAt(LocalDateTime.now());
        proposal.setStatus(RescheduleStatus.APPLIED);
        proposals
            .findByPlanIdOrderByIdDesc(planId)
            .stream()
            .filter(other -> other.getStatus().isActive())
            .forEach(other -> other.setStatus(RescheduleStatus.SUPERSEDED));
        notifications.notifyJoinedParticipants(
            plan,
            NotificationType.RESCHEDULE_APPLIED,
            "El organizador reprogramó " +
                plan.getName() +
                " para el " +
                proposal.getProposedDate() +
                " en " +
                proposal.getPlaceLabel() +
                ". Los votos afirmativos ya cuentan como confirmación. Revisa tu asistencia."
        );
        return response(proposal, user, electorate);
    }

    @PreAuthorize("@planAuthorization.isMember(#planId, authentication)")
    @Transactional
    public RescheduleDtos.Response withdraw(Long planId, Long proposalId) {
        User user = users.getCurrentUser();
        Plan plan = access.lockAsMember(planId, user);
        if (!isOrganizer(plan, user)) access.requireJoined(planId, user);
        RescheduleProposal proposal = proposal(planId, proposalId);
        Set<Long> electorate = electorate(planId);
        resolve(proposal, electorate);
        requireActive(proposal);
        if (
            !isOrganizer(plan, user) && !proposal.getCreatedBy().getId().equals(user.getId())
        ) throw new ForbiddenException("Only the author or organizer can withdraw this proposal");
        proposal.setStatus(RescheduleStatus.WITHDRAWN);
        return response(proposal, user, electorate);
    }

    private void preserveDuration(RescheduleProposal proposal, PlanOption currentDate) {
        LocalDate endDay = currentDate.getEffectiveEndDate();
        if (endDay == null) return;
        long dayShift = java.time.temporal.ChronoUnit.DAYS.between(
            currentDate.getEffectiveDate(),
            proposal.getProposedDate()
        );
        if (
            currentDate.getStartsAt() != null &&
            currentDate.getEndsAt() != null &&
            proposal.getProposedTime() != null
        ) {
            Duration duration = Duration.between(currentDate.getStartsAt(), currentDate.getEndsAt());
            LocalDateTime end = proposal.getProposedDate().atTime(proposal.getProposedTime()).plus(duration);
            proposal.setProposedEndDate(end.toLocalDate());
            proposal.setProposedEndTime(end.toLocalTime());
        } else proposal.setProposedEndDate(endDay.plusDays(dayShift));
    }

    private void resolve(RescheduleProposal proposal, Set<Long> electorate) {
        if (!proposal.getStatus().isActive()) return;
        Plan plan = proposal.getPlan();
        if (
            plan.getStatus() != PlanStatus.CLOSED ||
            proposal.getBaseAgreementVersion() != plan.getAgreementVersion()
        ) {
            proposal.setStatus(RescheduleStatus.SUPERSEDED);
            return;
        }
        if (!proposal.getExpiresAt().isAfter(Instant.now())) {
            proposal.setStatus(RescheduleStatus.EXPIRED);
            return;
        }
        List<RescheduleVote> votes = effectiveVotes(proposal, electorate);
        long yes = votes.stream().filter(RescheduleVote::isAccepted).count(),
            no = votes.size() - yes;
        if (no > electorate.size() / 2) {
            proposal.setStatus(RescheduleStatus.REJECTED);
            notifications.notifyJoinedParticipants(
                plan,
                NotificationType.RESCHEDULE_REJECTED,
                "La propuesta del " +
                    proposal.getProposedDate() +
                    " para " +
                    plan.getName() +
                    " fue rechazada por mayoría. El acuerdo vigente se conserva."
            );
        } else proposal.setStatus(
            yes > electorate.size() / 2 && yes >= plan.getMinParticipants()
                ? RescheduleStatus.APPROVED
                : RescheduleStatus.VOTING
        );
    }

    private RescheduleDtos.Response response(RescheduleProposal p, User user, Set<Long> electorate) {
        List<RescheduleVote> votes = effectiveVotes(p, electorate);
        long yes = votes.stream().filter(RescheduleVote::isAccepted).count();
        Boolean mine = votes
            .stream()
            .filter(v -> v.getUser().getId().equals(user.getId()))
            .map(RescheduleVote::isAccepted)
            .findFirst()
            .orElse(null);
        return new RescheduleDtos.Response(
            p.getId(),
            p.getPlan().getId(),
            p.getCreatedBy().getId(),
            p.getCreatedBy().getName(),
            p.getBaseAgreementVersion(),
            p.getProposedDate(),
            p.getProposedTime(),
            p.getProposedEndDate(),
            p.getProposedEndTime(),
            p.getPlaceOptionId(),
            p.getPlaceLabel(),
            location(p),
            p.getReason(),
            p.getStatus(),
            yes,
            votes.size() - yes,
            electorate.size(),
            Math.max(electorate.size() / 2 + 1, p.getPlan().getMinParticipants()),
            mine,
            p.getStatus() == RescheduleStatus.APPROVED && isOrganizer(p.getPlan(), user),
            p.getStatus().isActive() &&
                (isOrganizer(p.getPlan(), user) || p.getCreatedBy().getId().equals(user.getId())),
            p.getCreatedAt(),
            p.getExpiresAt()
        );
    }

    private List<RescheduleVote> effectiveVotes(RescheduleProposal proposal, Set<Long> electorate) {
        return ballots
            .findByProposalId(proposal.getId())
            .stream()
            .filter(v -> electorate.contains(v.getUser().getId()))
            .toList();
    }

    private Set<Long> electorate(Long planId) {
        return participants
            .findByPlanId(planId)
            .stream()
            .filter(p -> p.getStatus() == ParticipationStatus.JOINED)
            .map(p -> p.getUser().getId())
            .collect(Collectors.toSet());
    }

    private RescheduleProposal proposal(Long planId, Long id) {
        return proposals
            .findById(id)
            .filter(p -> p.getPlan().getId().equals(planId))
            .orElseThrow(() -> new ResourceNotFoundException("Proposal not found in this plan"));
    }

    private void saveVote(RescheduleProposal proposal, User user, boolean accepted) {
        RescheduleVote ballot = ballots
            .findByProposalIdAndUserId(proposal.getId(), user.getId())
            .orElseGet(RescheduleVote::new);
        ballot.setProposal(proposal);
        ballot.setUser(user);
        ballot.setAccepted(accepted);
        ballot.setUpdatedAt(Instant.now());
        ballots.saveAndFlush(ballot);
    }

    private PlanOption selected(Long planId, OptionType type) {
        return options
            .findByPlanIdAndType(planId, type)
            .stream()
            .filter(PlanOption::isSelected)
            .findFirst()
            .orElseThrow(() ->
                new InvalidOperationException("Reopen the plan to choose its missing day or place")
            );
    }

    private void requireConfirmed(Plan plan) {
        if (plan.getStatus() != PlanStatus.CLOSED) throw new InvalidOperationException(
            "Only a confirmed plan accepts rescheduling proposals"
        );
    }

    private void requireActive(RescheduleProposal proposal) {
        if (!proposal.getStatus().isActive()) throw new ConflictException(
            "This proposal is no longer open for voting"
        );
    }

    private boolean isOrganizer(Plan plan, User user) {
        return user.isAdmin() || plan.getCreator().getId().equals(user.getId());
    }

    private void copyLocation(RescheduleProposal proposal, LocationDto location) {
        proposal.setLatitude(location.getLatitude());
        proposal.setLongitude(location.getLongitude());
        proposal.setAddress(location.getAddress());
    }

    private LocationDto location(RescheduleProposal proposal) {
        LocationDto location = new LocationDto();
        location.setLatitude(proposal.getLatitude());
        location.setLongitude(proposal.getLongitude());
        location.setAddress(proposal.getAddress());
        return location;
    }
}
