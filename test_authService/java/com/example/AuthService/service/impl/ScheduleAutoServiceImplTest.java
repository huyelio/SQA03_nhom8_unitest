package com.example.AuthService.service.impl;

import com.example.AuthService.repository.DrugInPrescriptionRepository;
import com.example.AuthService.repository.ScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test {@link ScheduleAutoServiceImpl}. CheckDB: verify {@link ScheduleRepository#autoMarkSkipped}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleAutoServiceImpl")
class ScheduleAutoServiceImplTest {

    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private DrugInPrescriptionRepository drugInPrescriptionRepository;

    @InjectMocks
    private ScheduleAutoServiceImpl scheduleAutoService;

    /** Test Case ID: TC-SCHED-AUTO-01 */
    @Test
    @DisplayName("autoMarkSkippedSchedules calls repository autoMarkSkipped and extend scan")
    void autoMarkSkippedSchedulesInvokesRepositories() {
        when(scheduleRepository.autoMarkSkipped(any(LocalDateTime.class))).thenReturn(3);
        when(drugInPrescriptionRepository.findByEndDateIsNull()).thenReturn(java.util.Collections.emptyList());

        scheduleAutoService.autoMarkSkippedSchedules();

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(scheduleRepository, times(1)).autoMarkSkipped(startCaptor.capture());
        assertNotNull(startCaptor.getValue());
        verify(drugInPrescriptionRepository).findByEndDateIsNull();
    }
}
