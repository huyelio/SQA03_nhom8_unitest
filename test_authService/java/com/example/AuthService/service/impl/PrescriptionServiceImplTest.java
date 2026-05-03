package com.example.AuthService.service.impl;

import com.example.AuthService.dto.request.DrugInPresRequest;
import com.example.AuthService.dto.request.PrescriptionRequest;
import com.example.AuthService.entity.Prescription;
import com.example.AuthService.entity.Unit;
import com.example.AuthService.entity.User;
import com.example.AuthService.enums.FrequencyType;
import com.example.AuthService.repository.DrugInPrescriptionRepository;
import com.example.AuthService.repository.DrugRepository;
import com.example.AuthService.repository.PrescriptionRepository;
import com.example.AuthService.repository.ScheduleRepository;
import com.example.AuthService.repository.UnitRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test {@link PrescriptionServiceImpl} (luồng tạo đơn thuốc). CheckDB: verify save trên prescription / drugInPres / schedules (mock).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PrescriptionServiceImpl")
class PrescriptionServiceImplTest {

    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private DrugInPrescriptionRepository drugInPrescriptionRepository;
    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private DrugRepository drugRepository;
    @Mock
    private UnitRepository unitRepository;

    @InjectMocks
    private PrescriptionServiceImpl prescriptionService;

    private User patient() {
        return User.builder().id(1L).email("p@p.com").name("P").password("x")
                .enabled(true).accountNonExpired(true).accountNonLocked(true).credentialsNonExpired(true).build();
    }

    /** Test Case ID: TC-RX-CREATE-01 */
    @Test
    @DisplayName("createPrescription throws when user null")
    void createPrescriptionThrowsWhenUserNull() {
        PrescriptionRequest request = PrescriptionRequest.builder().name("Rx").drugs(List.of()).build();
        assertThrows(IllegalArgumentException.class, () -> prescriptionService.createPrescription(request, null));
    }

    /** Test Case ID: TC-RX-CREATE-02 */
    @Test
    @DisplayName("createPrescription throws when drugs empty")
    void createPrescriptionThrowsWhenDrugsEmpty() {
        PrescriptionRequest request = PrescriptionRequest.builder().name("Rx").drugs(List.of()).build();
        assertThrows(IllegalArgumentException.class, () -> prescriptionService.createPrescription(request, patient()));
    }

    /** Test Case ID: TC-RX-CREATE-03 */
    @Test
    @DisplayName("createPrescription saves prescription drug line and schedules")
    void createPrescriptionSavesHierarchy() {
        Unit unit = new Unit();
        unit.setId(1L);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(inv -> {
            Prescription p = inv.getArgument(0);
            p.setId(50L);
            return p;
        });
        when(drugInPrescriptionRepository.save(any())).thenAnswer(inv -> {
            com.example.AuthService.entity.DrugInPrescription dip = inv.getArgument(0);
            dip.setId(60L);
            return dip;
        });

        DrugInPresRequest drugLine = DrugInPresRequest.builder()
                .unitId(1L)
                .drugName("Vitamin C")
                .startDate("2026-01-01")
                .endDate("2026-01-02")
                .frequencyType(FrequencyType.DAILY)
                .build();

        PrescriptionRequest request = PrescriptionRequest.builder()
                .name("Đơn 1")
                .drugs(List.of(drugLine))
                .build();

        Prescription saved = prescriptionService.createPrescription(request, patient());

        assertEquals(50L, saved.getId());
        verify(prescriptionRepository, times(1)).save(any(Prescription.class));
        verify(drugInPrescriptionRepository, times(1)).save(any());
        ArgumentCaptor<List<com.example.AuthService.entity.Schedule>> scheduleListCaptor = ArgumentCaptor.forClass(List.class);
        verify(scheduleRepository).saveAll(scheduleListCaptor.capture());
        assertFalse(scheduleListCaptor.getValue().isEmpty());
    }
}
