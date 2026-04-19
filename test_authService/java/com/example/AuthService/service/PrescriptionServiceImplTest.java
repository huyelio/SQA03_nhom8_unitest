package com.example.AuthService.service;

import com.example.AuthService.dto.request.DrugInPresRequest;
import com.example.AuthService.dto.request.PrescriptionRequest;
import com.example.AuthService.dto.request.ScheduleAddRequest;
import com.example.AuthService.entity.*;
import com.example.AuthService.enums.FrequencyType;
import com.example.AuthService.repository.*;
import com.example.AuthService.service.impl.PrescriptionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho PrescriptionServiceImpl.
 * Sử dụng Mockito — không gọi DB thật.
 *
 * Test cases:
 *   TC-PRES-01: createPrescription hợp lệ với 1 thuốc DAILY → save gọi đúng
 *   TC-PRES-02: createPrescription — user = null → IllegalArgumentException
 *   TC-PRES-03: createPrescription — danh sách thuốc rỗng → IllegalArgumentException
 *   TC-PRES-04: createPrescription — unitId không tồn tại → IllegalArgumentException
 *   TC-PRES-05: createPrescription — startDate null → IllegalArgumentException
 *   TC-PRES-06: createPrescription — thuốc tần suất INTERVAL, sinh schedule cách 2 ngày
 *   TC-PRES-07: deletePrescription — đơn thuốc không thuộc user → RuntimeException
 *   TC-PRES-08: togglePrescriptionStatus — đảo từ active(1) → inactive(0)
 *   TC-PRES-09: createSingleDrug — tạo thuốc đơn không gắn prescription, user hợp lệ
 *   TC-PRES-10: createSingleDrug — unitId không tồn tại → IllegalArgumentException
 */
@ExtendWith(MockitoExtension.class)
class PrescriptionServiceImplTest {

    @Mock private PrescriptionRepository         prescriptionRepository;
    @Mock private DrugInPrescriptionRepository   drugInPrescriptionRepository;
    @Mock private ScheduleRepository             scheduleRepository;
    @Mock private DrugRepository                 drugRepository;
    @Mock private UnitRepository                 unitRepository;

    @InjectMocks
    private PrescriptionServiceImpl prescriptionService;

    private User   testUser;
    private Unit   testUnit;
    private Role   userRole;

    @BeforeEach
    void setUp() {
        userRole = new Role();
        userRole.setId(1L);
        userRole.setName("USER");

        testUser = User.builder()
                .id(1L)
                .email("user@test.com")
                .role(userRole)
                .build();

        testUnit = new Unit();
        testUnit.setId(1L);
        testUnit.setName("Viên");
    }

    // ─────────────────────────── createPrescription ─────────────────────────

    /**
     * TC-PRES-01
     * Mục đích : createPrescription với 1 thuốc DAILY và 1 khung giờ uống → save Prescription + DrugInPrescription + Schedules
     * Input     : PrescriptionRequest hợp lệ; unitRepository.findById → unit; startDate=2024-06-01, endDate=2024-06-03
     * Expected  : prescriptionRepository.save gọi 1 lần; drugInPrescriptionRepository.save gọi 1 lần; scheduleRepository.saveAll gọi
     * CheckDB   : Yes — verify save trên 3 repository
     * Rollback  : Mockito — không ghi DB thật
     */
    @Test
    @DisplayName("TC-PRES-01: createPrescription hợp lệ, 1 thuốc DAILY")
    void createPrescription_valid_savesAllEntities() {
        // Arrange
        ScheduleAddRequest schedule = new ScheduleAddRequest();
        schedule.setTime("08:00");
        schedule.setDosage(1.0);

        DrugInPresRequest drugReq = DrugInPresRequest.builder()
                .drugName("Paracetamol")
                .unitId(1L)
                .startDate("2024-06-01")
                .endDate("2024-06-03")
                .frequencyType(FrequencyType.DAILY)
                .schedules(List.of(schedule))
                .build();

        PrescriptionRequest request = PrescriptionRequest.builder()
                .name("Đơn khám định kỳ")
                .hospital("Bệnh viện Chợ Rẫy")
                .doctorName("BS. Nguyen")
                .consultationDate("2024-06-01")
                .drugs(List.of(drugReq))
                .build();

        // Stub repo để trả đối tượng đã lưu
        Prescription savedPres = new Prescription();
        savedPres.setId(1L);
        savedPres.setName("Đơn khám định kỳ");
        savedPres.setUser(testUser);
        savedPres.setDrugInPrescriptions(new ArrayList<>());

        given(prescriptionRepository.save(any(Prescription.class))).willReturn(savedPres);
        given(unitRepository.findById(1L)).willReturn(Optional.of(testUnit));

        DrugInPrescription savedDip = new DrugInPrescription();
        savedDip.setId(10L);
        savedDip.setSchedules(new ArrayList<>());
        given(drugInPrescriptionRepository.save(any(DrugInPrescription.class))).willReturn(savedDip);
        given(scheduleRepository.saveAll(anyList())).willReturn(List.of());

        // Act
        Prescription result = prescriptionService.createPrescription(request, testUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);

        // CheckDB: 3 lần save — prescription, drugInPrescription, schedules
        verify(prescriptionRepository, times(1)).save(any(Prescription.class));
        verify(drugInPrescriptionRepository, times(1)).save(any(DrugInPrescription.class));
        verify(scheduleRepository, times(1)).saveAll(anyList());
    }

    /**
     * TC-PRES-02
     * Mục đích : createPrescription với user = null → IllegalArgumentException
     * Input     : user = null
     * Expected  : IllegalArgumentException "User must not be null"
     * CheckDB   : No — không truy cập DB
     */
    @Test
    @DisplayName("TC-PRES-02: createPrescription user null → IllegalArgumentException")
    void createPrescription_nullUser_throwsException() {
        // Arrange
        PrescriptionRequest request = PrescriptionRequest.builder()
                .name("Test")
                .drugs(List.of(new DrugInPresRequest()))
                .build();

        // Act & Assert
        assertThatThrownBy(() -> prescriptionService.createPrescription(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User must not be null");

        verify(prescriptionRepository, never()).save(any());
    }

    /**
     * TC-PRES-03
     * Mục đích : createPrescription — danh sách thuốc rỗng → IllegalArgumentException
     * Input     : drugs = []
     * Expected  : IllegalArgumentException "Prescription must contain at least one drug"
     */
    @Test
    @DisplayName("TC-PRES-03: createPrescription — drugs rỗng → IllegalArgumentException")
    void createPrescription_emptyDrugs_throwsException() {
        // Arrange
        PrescriptionRequest request = PrescriptionRequest.builder()
                .name("Đơn rỗng")
                .drugs(List.of()) // rỗng
                .build();

        // Act & Assert
        assertThatThrownBy(() -> prescriptionService.createPrescription(request, testUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one drug");

        verify(prescriptionRepository, never()).save(any());
    }

    /**
     * TC-PRES-04
     * Mục đích : createPrescription — unitId không tồn tại → IllegalArgumentException
     * Input     : drugs=[{unitId=999}]; unitRepository.findById → empty
     * Expected  : IllegalArgumentException "Unit not found"
     * CheckDB   : Yes — verify unitRepository.findById gọi
     */
    @Test
    @DisplayName("TC-PRES-04: createPrescription — unitId không tồn tại → IllegalArgumentException")
    void createPrescription_unitNotFound_throwsException() {
        // Arrange
        DrugInPresRequest drugReq = DrugInPresRequest.builder()
                .drugName("Vitamin C")
                .unitId(999L) // không tồn tại
                .startDate("2024-06-01")
                .frequencyType(FrequencyType.DAILY)
                .schedules(List.of())
                .build();

        PrescriptionRequest request = PrescriptionRequest.builder()
                .name("Đơn Vitamin")
                .drugs(List.of(drugReq))
                .build();

        Prescription savedPres = new Prescription();
        savedPres.setId(1L);
        savedPres.setDrugInPrescriptions(new ArrayList<>());
        given(prescriptionRepository.save(any(Prescription.class))).willReturn(savedPres);
        given(unitRepository.findById(999L)).willReturn(Optional.empty()); // không có unit

        // Act & Assert
        assertThatThrownBy(() -> prescriptionService.createPrescription(request, testUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unit not found");

        // CheckDB: unitRepository.findById được gọi
        verify(unitRepository).findById(999L);
    }

    /**
     * TC-PRES-05
     * Mục đích : createPrescription — startDate null → IllegalArgumentException
     * Input     : drugs=[{unitId=1, startDate=null}]
     * Expected  : IllegalArgumentException "Start date must not be null"
     */
    @Test
    @DisplayName("TC-PRES-05: createPrescription — startDate null → IllegalArgumentException")
    void createPrescription_nullStartDate_throwsException() {
        // Arrange
        DrugInPresRequest drugReq = DrugInPresRequest.builder()
                .drugName("Ibuprofen")
                .unitId(1L)
                .startDate(null) // null start date
                .frequencyType(FrequencyType.DAILY)
                .schedules(List.of())
                .build();

        PrescriptionRequest request = PrescriptionRequest.builder()
                .name("Đơn Ibuprofen")
                .drugs(List.of(drugReq))
                .build();

        Prescription savedPres = new Prescription();
        savedPres.setId(1L);
        savedPres.setDrugInPrescriptions(new ArrayList<>());
        given(prescriptionRepository.save(any(Prescription.class))).willReturn(savedPres);
        given(unitRepository.findById(1L)).willReturn(Optional.of(testUnit));

        // Act & Assert
        assertThatThrownBy(() -> prescriptionService.createPrescription(request, testUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start date must not be null");
    }

    /**
     * TC-PRES-06
     * Mục đích : createPrescription — thuốc INTERVAL (interval_days=2), startDate đến endDate 5 ngày →
     *            sinh schedule cho ngày 1, 3, 5 (3 ngày cách 2)
     * Input     : frequencyType=INTERVAL, intervalDays=2, start=2024-06-01, end=2024-06-05
     * Expected  : scheduleRepository.saveAll gọi với list chứa 3 schedule
     * CheckDB   : Yes — verify scheduleRepository.saveAll với đúng số schedule
     */
    @Test
    @DisplayName("TC-PRES-06: createPrescription INTERVAL 2 ngày — sinh đúng số schedule")
    void createPrescription_intervalFrequency_generatesCorrectSchedules() {
        // Arrange
        ScheduleAddRequest sr = new ScheduleAddRequest();
        sr.setTime("08:00");
        sr.setDosage(1.0);

        DrugInPresRequest drugReq = DrugInPresRequest.builder()
                .drugName("Antibiotic")
                .unitId(1L)
                .startDate("2024-06-01")
                .endDate("2024-06-05")  // 5 ngày: 1,3,5 = 3 ngày
                .frequencyType(FrequencyType.INTERVAL)
                .intervalDays(2)
                .schedules(List.of(sr))
                .build();

        PrescriptionRequest request = PrescriptionRequest.builder()
                .name("Đơn kháng sinh")
                .drugs(List.of(drugReq))
                .build();

        Prescription savedPres = new Prescription();
        savedPres.setId(1L);
        savedPres.setDrugInPrescriptions(new ArrayList<>());
        given(prescriptionRepository.save(any(Prescription.class))).willReturn(savedPres);
        given(unitRepository.findById(1L)).willReturn(Optional.of(testUnit));

        DrugInPrescription savedDip = new DrugInPrescription();
        savedDip.setId(10L);
        savedDip.setSchedules(new ArrayList<>());
        given(drugInPrescriptionRepository.save(any(DrugInPrescription.class))).willReturn(savedDip);

        // Capture schedules được save
        ArgumentCaptor<List<Schedule>> captor = ArgumentCaptor.forClass(List.class);
        given(scheduleRepository.saveAll(captor.capture())).willReturn(List.of());

        // Act
        prescriptionService.createPrescription(request, testUser);

        // Assert: 3 ngày × 1 giờ = 3 schedule
        List<Schedule> capturedSchedules = captor.getValue();
        assertThat(capturedSchedules).hasSize(3);
        // CheckDB: verify scheduleRepository.saveAll gọi đúng 1 lần
        verify(scheduleRepository, times(1)).saveAll(anyList());
    }

    // ─────────────────────────── deletePrescription ─────────────────────────

    /**
     * TC-PRES-07
     * Mục đích : deletePrescription — đơn thuốc không thuộc user → RuntimeException "không có quyền xoá"
     * Input     : prescriptionId=1; prescriptionRepo.findByIdAndUser → Optional.empty()
     * Expected  : RuntimeException chứa "không có quyền xoá"
     * CheckDB   : Yes — verify prescriptionRepository.findByIdAndUser gọi
     */
    @Test
    @DisplayName("TC-PRES-07: deletePrescription — không thuộc user → RuntimeException")
    void deletePrescription_notOwner_throwsException() {
        // Arrange
        given(prescriptionRepository.findByIdAndUser(1L, testUser))
                .willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> prescriptionService.deletePrescription(1L, testUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("quyền xoá");

        // CheckDB: prescriptionRepository.findByIdAndUser được gọi
        verify(prescriptionRepository).findByIdAndUser(1L, testUser);
        verify(prescriptionRepository, never()).delete(any());
    }

    // ─────────────────────────── togglePrescriptionStatus ───────────────────

    /**
     * TC-PRES-08
     * Mục đích : togglePrescriptionStatus — status từ 1 (active) → 0 (inactive)
     * Input     : prescriptionId=1; prescription.status=1, thuộc testUser
     * Expected  : prescription.status = 0; prescriptionRepository.save gọi
     * CheckDB   : Yes — verify save với status=0
     * Rollback  : Mockito
     */
    @Test
    @DisplayName("TC-PRES-08: togglePrescriptionStatus — active(1) → inactive(0)")
    void togglePrescriptionStatus_activeToInactive() {
        // Arrange
        Prescription prescription = new Prescription();
        prescription.setId(1L);
        prescription.setUser(testUser);
        prescription.setStatus(1); // active

        given(prescriptionRepository.findById(1L)).willReturn(Optional.of(prescription));
        given(prescriptionRepository.save(any(Prescription.class))).willReturn(prescription);

        // Act
        Prescription result = prescriptionService.togglePrescriptionStatus(1L, testUser);

        // Assert
        assertThat(prescription.getStatus()).isEqualTo(0);
        // CheckDB: verify save được gọi với status=0
        verify(prescriptionRepository).save(argThat((Prescription p) -> p.getStatus() == 0));
    }

    // ─────────────────────────── createSingleDrug ────────────────────────────

    /**
     * TC-PRES-09
     * Mục đích : createSingleDrug — tạo thuốc đơn không gắn prescription → DrugInPrescription.prescription=null
     * Input     : DrugInPresRequest hợp lệ, user hợp lệ
     * Expected  : drugInPrescriptionRepository.save gọi; DrugInPrescription.prescription = null
     * CheckDB   : Yes — verify drugInPrescriptionRepository.save
     * Rollback  : Mockito
     */
    @Test
    @DisplayName("TC-PRES-09: createSingleDrug — tạo thuốc đơn, prescription = null")
    void createSingleDrug_validRequest_prescriptionIsNull() {
        // Arrange
        ScheduleAddRequest sr = new ScheduleAddRequest();
        sr.setTime("08:00");
        sr.setDosage(1.0);

        DrugInPresRequest drugReq = DrugInPresRequest.builder()
                .drugName("Aspirin")
                .unitId(1L)
                .startDate("2024-06-01")
                .frequencyType(FrequencyType.DAILY)
                .schedules(List.of(sr))
                .build();

        given(unitRepository.findById(1L)).willReturn(Optional.of(testUnit));

        DrugInPrescription savedDip = new DrugInPrescription();
        savedDip.setId(20L);
        savedDip.setPrescription(null); // không gắn đơn thuốc
        savedDip.setSchedules(new ArrayList<>());
        given(drugInPrescriptionRepository.save(any(DrugInPrescription.class))).willReturn(savedDip);
        given(scheduleRepository.saveAll(anyList())).willReturn(List.of());

        // Act
        DrugInPrescription result = prescriptionService.createSingleDrug(drugReq, testUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getPrescription()).isNull(); // đây là thuốc đơn
        // CheckDB: verify drugInPrescriptionRepository.save được gọi
        verify(drugInPrescriptionRepository).save(
                argThat((DrugInPrescription dip) -> dip.getPrescription() == null)
        );
    }

    /**
     * TC-PRES-10
     * Mục đích : createSingleDrug — unitId không tồn tại → IllegalArgumentException
     * Input     : unitId=999; unitRepository.findById → empty
     * Expected  : IllegalArgumentException "Unit not found"
     * CheckDB   : Yes — verify unitRepository.findById
     */
    @Test
    @DisplayName("TC-PRES-10: createSingleDrug — unitId không tồn tại → IllegalArgumentException")
    void createSingleDrug_unitNotFound_throwsException() {
        // Arrange
        DrugInPresRequest drugReq = DrugInPresRequest.builder()
                .drugName("Vitamin D")
                .unitId(999L)
                .startDate("2024-06-01")
                .frequencyType(FrequencyType.DAILY)
                .schedules(List.of())
                .build();

        given(unitRepository.findById(999L)).willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> prescriptionService.createSingleDrug(drugReq, testUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unit not found");

        // CheckDB: verify unitRepository.findById gọi
        verify(unitRepository).findById(999L);
        verify(drugInPrescriptionRepository, never()).save(any());
    }
}
