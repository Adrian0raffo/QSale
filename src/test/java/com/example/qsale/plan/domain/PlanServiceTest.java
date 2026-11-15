package com.example.qsale.plan.domain;

import com.example.qsale.exceptions.InvalidOperationException;
import com.example.qsale.option.domain.PlanOptionService;
import com.example.qsale.plan.infrastructure.PlanRepository;
import com.example.qsale.plan.dto.PlanDetailDto;
import com.example.qsale.participant.infrastructure.PlanParticipantRepository;
import com.example.qsale.user.domain.Role;
import com.example.qsale.user.domain.User;
import com.example.qsale.user.domain.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;
    @Mock
    private PlanParticipantRepository participantRepository;
    @Mock
    private PlanAccessService planAccessService;
    @Mock
    private FeasibilityService feasibilityService;
    @Mock
    private PlanOptionService planOptionService;
    @Mock
    private UserService userService;
    @Mock
    private ModelMapper modelMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PlanService planService;

    @BeforeEach
    void setUp() {
        planService = new PlanService(planRepository, participantRepository, planAccessService, feasibilityService,
                planOptionService, userService, modelMapper, eventPublisher,
                org.mockito.Mockito.mock(com.example.qsale.commitment.infrastructure.CommitmentResponseRepository.class));
    }

    @Test
    void rejectsNegativePage() {
        assertThrows(InvalidOperationException.class,
                () -> planService.getMyPlans(null, -1, 10, "", "createdAt,desc"));
        verifyNoInteractions(userService, planRepository);
    }

    @Test
    void rejectsNonPositivePageSize() {
        assertThrows(InvalidOperationException.class,
                () -> planService.getMyPlans(null, 0, 0, "", "createdAt,desc"));
        verifyNoInteractions(userService, planRepository);
    }

    @Test
    void capsPageSizeAtOneHundred() {
        User user = new User();
        user.setId(1L);
        user.setRole(Role.USER);
        when(userService.getCurrentUser()).thenReturn(user);
        when(planRepository.searchMyPlans(any(), any(), any(), any())).thenReturn(Page.empty());

        planService.getMyPlans(null, 0, 1000, "", "createdAt,desc");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(planRepository).searchMyPlans(any(), any(), any(), pageableCaptor.capture());
        assertEquals(100, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void closesReadyPlanAndSelectsBestOptions() {
        Plan plan = new Plan();
        plan.setId(1L);
        plan.setStatus(PlanStatus.OPEN);
        when(userService.getCurrentUser()).thenReturn(new User());
        when(planAccessService.lockAsOrganizer(any(), any())).thenReturn(plan);
        doAnswer(invocation -> {
            plan.setFeasibilityScore(80);
            plan.setFeasibilityStatus(FeasibilityStatus.READY_TO_CLOSE);
            return null;
        }).when(feasibilityService).updateFeasibility(plan);
        when(planRepository.save(plan)).thenReturn(plan);
        when(modelMapper.map(plan, PlanDetailDto.class)).thenReturn(new PlanDetailDto());

        planService.closePlan(1L);

        verify(planOptionService).selectBestOptions(plan);
        assertEquals(PlanStatus.CLOSED, plan.getStatus());
    }

    @Test
    void rejectsClosingPlanThatIsNotReady() {
        Plan plan = new Plan();
        plan.setId(1L);
        plan.setStatus(PlanStatus.OPEN);
        plan.setFeasibilityStatus(FeasibilityStatus.NOT_VIABLE);
        when(userService.getCurrentUser()).thenReturn(new User());
        when(planAccessService.lockAsOrganizer(any(), any())).thenReturn(plan);

        assertThrows(InvalidOperationException.class, () -> planService.closePlan(1L));

        verify(planOptionService, never()).selectBestOptions(any());
        verify(planRepository, never()).save(any());
    }
}
