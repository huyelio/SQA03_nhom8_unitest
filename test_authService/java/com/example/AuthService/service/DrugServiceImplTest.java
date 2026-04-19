package com.example.AuthService.service;

import com.example.AuthService.dto.DrugFilter;
import com.example.AuthService.dto.response.DrugResponse;
import com.example.AuthService.entity.Drug;
import com.example.AuthService.repository.DrugRepository;
import com.example.AuthService.service.impl.DrugServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit tests cho DrugServiceImpl.
 * Sử dụng Mockito (không cần Spring context).
 *
 * Test cases:
 *   TC-DRUG-01: getDrugs – user thường chỉ thấy thuốc active
 *   TC-DRUG-02: getDrugs – admin thấy tất cả thuốc kể cả inactive
 *   TC-DRUG-03: updateDrugActive – bật active → CheckDB save với isActive=true
 *   TC-DRUG-04: deleteDrug – thuốc không tồn tại → exception
 *   TC-DRUG-05: suggestNames – limit vượt 20 bị giới hạn thành 10
 */
@ExtendWith(MockitoExtension.class)
class DrugServiceImplTest {

    // ==================== MOCKS ====================

    @Mock private DrugRepository drugRepository;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private InventoryService inventoryService;

    @InjectMocks
    private DrugServiceImpl drugService;

    // ==================== TEST DATA ====================

    private Drug activeDrug;
    private Drug inactiveDrug;

    @BeforeEach
    void setUp() {
        activeDrug = Drug.builder()
                .id(1L).name("Paracetamol 500mg").title("Hạ sốt giảm đau")
                .price(BigDecimal.valueOf(50000)).importPrice(BigDecimal.valueOf(30000))
                .soldQuantity(10).isActive(true)
                .build();

        inactiveDrug = Drug.builder()
                .id(2L).name("Drug Inactive").title("Thuốc ngưng sản xuất")
                .price(BigDecimal.valueOf(100000)).importPrice(BigDecimal.valueOf(70000))
                .soldQuantity(0).isActive(false)
                .build();
    }

    // ==================== TEST METHODS ====================

    /**
     * TC-DRUG-01: User thường gọi getDrugs → filter.isActive được set true.
     * Input    : isAdmin = false, filter mặc định
     * Expected : Thuốc trả về đều có isActive = true
     * CheckDB  : drugRepository.findAll(spec, pageable) được gọi
     */
    @Test
    @DisplayName("TC-DRUG-01: getDrugs user thường chỉ trả thuốc active")
    @SuppressWarnings("unchecked")
    void getDrugs_asNonAdmin_setsIsActiveFilterTrue() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Drug> drugPage = new PageImpl<>(List.of(activeDrug), pageable, 1);

        given(drugRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(drugPage);
        given(inventoryService.getTotalImportedForDrugs(any()))
                .willReturn(Map.of(1L, 50));

        DrugFilter filter = new DrugFilter();

        // Act
        Page<DrugResponse> result = drugService.getDrugs(filter, pageable, false);

        // Assert: tất cả thuốc trả về đều active
        assertThat(result.getContent()).allMatch(DrugResponse::getIsActive);

        // CheckDB: drugRepository.findAll được gọi
        verify(drugRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    /**
     * TC-DRUG-02: Admin gọi getDrugs → trả cả thuốc inactive.
     * Input    : isAdmin = true
     * Expected : Cả active và inactive đều được trả về (không lọc isActive)
     */
    @Test
    @DisplayName("TC-DRUG-02: getDrugs admin thấy tất cả thuốc")
    @SuppressWarnings("unchecked")
    void getDrugs_asAdmin_returnsAllDrugsIncludingInactive() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Drug> drugPage = new PageImpl<>(List.of(activeDrug, inactiveDrug), pageable, 2);

        given(drugRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(drugPage);
        given(inventoryService.getTotalImportedForDrugs(any()))
                .willReturn(Map.of(1L, 50, 2L, 5));

        DrugFilter filter = new DrugFilter();

        // Act
        Page<DrugResponse> result = drugService.getDrugs(filter, pageable, true);

        // Assert: 2 thuốc được trả về, gồm cả thuốc inactive
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    /**
     * TC-DRUG-03: Bật active cho thuốc đang inactive → CheckDB save với isActive=true.
     * Input    : id = 2 (inactiveDrug), active = true
     * Expected : Thuốc được lưu với isActive = true
     * CheckDB  : drugRepository.save gọi với drug.isActive = true
     */
    @Test
    @DisplayName("TC-DRUG-03: updateDrugActive bật active lưu vào DB")
    void updateDrugActive_withInactiveDrug_setsActiveAndSaves() {
        // Arrange
        given(drugRepository.findById(2L)).willReturn(Optional.of(inactiveDrug));
        given(drugRepository.save(any(Drug.class))).willReturn(inactiveDrug);

        // Act
        Drug result = drugService.updateDrugActive(2L, true);

        // Assert CheckDB: save được gọi với isActive = true
        verify(drugRepository).save(argThat(drug -> Boolean.TRUE.equals(drug.getIsActive())));
    }

    /**
     * TC-DRUG-04: Xóa thuốc không tồn tại → ném RuntimeException.
     * Input    : id = 999 không có trong DB
     * Expected : RuntimeException với message "Drug not found"
     * CheckDB  : drugRepository.deleteById KHÔNG được gọi
     */
    @Test
    @DisplayName("TC-DRUG-04: deleteDrug thuốc không tồn tại ném exception")
    void deleteDrug_withNonExistingId_throwsException() {
        // Arrange
        given(drugRepository.existsById(999L)).willReturn(false);

        // Act + Assert
        assertThatThrownBy(() -> drugService.deleteDrug(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Drug not found");

        // CheckDB: deleteById không được gọi
        verify(drugRepository, org.mockito.Mockito.never()).deleteById(any());
    }

    /**
     * TC-DRUG-05: suggestNames với limit > 20 bị giới hạn xuống còn 10.
     * Input    : q = "para", limit = 100 (vượt giới hạn)
     * Expected : drugRepository.suggestNames được gọi với size = 10
     */
    @Test
    @DisplayName("TC-DRUG-05: suggestNames giới hạn limit về 10 khi > 20")
    void suggestNames_withOverLimit_capsResultTo10() {
        // Arrange
        given(drugRepository.suggestNames(any(), any()))
                .willReturn(List.of("Paracetamol 500mg", "Paracetamol 1g"));

        // Act
        List<String> result = drugService.suggestNames("para", 100);

        // Assert: hàm trả về danh sách từ repository
        assertThat(result).isNotNull();

        // Xác minh rằng PageRequest.of(0, 10) được dùng (size = 10 khi limit > 20)
        verify(drugRepository).suggestNames(
                eq("para"),
                argThat((Pageable pageRequest) -> pageRequest.getPageSize() == 10)
        );
    }

    // ─────────────────── Bổ sung test case (edge cases) ────────────────────

    /**
     * TC-DRUG-06
     * Mục đích : deleteDrug — drugId tồn tại → deleteById gọi đúng
     * Input     : drugId = 1L; drugRepository.findById → drug
     * Expected  : drugRepository.deleteById(1L) gọi 1 lần
     * CheckDB   : Yes — verify deleteById
     * Rollback  : Mockito — không xóa DB thật
     */
    @Test
    @DisplayName("TC-DRUG-06: deleteDrug — drug tồn tại → deleteById gọi")
    void deleteDrug_exists_callsDeleteById() {
        // Arrange — deleteDrug dùng existsById, không phải findById
        given(drugRepository.existsById(1L)).willReturn(true);

        // Act
        drugService.deleteDrug(1L);

        // Assert — CheckDB: verify deleteById được gọi với id=1
        verify(drugRepository).deleteById(1L);
    }

    /**
     * TC-DRUG-07
     * Mục đích : updateDrugActive — drugId không tồn tại → RuntimeException
     * Input     : drugId = 999L; drugRepository.findById → empty
     * Expected  : RuntimeException "Drug not found"
     * CheckDB   : Yes — verify findById; save KHÔNG gọi
     */
    @Test
    @DisplayName("TC-DRUG-07: updateDrugActive drug không tồn tại → RuntimeException")
    void updateDrugActive_notFound_throwsException() {
        // Arrange
        given(drugRepository.findById(999L)).willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> drugService.updateDrugActive(999L, true))
                .isInstanceOf(RuntimeException.class);

        // CheckDB: save không được gọi
        verify(drugRepository, org.mockito.Mockito.never()).save(any());
    }

    /**
     * TC-DRUG-08
     * Mục đích : suggestNames — keyword rỗng → gọi repository với keyword rỗng
     * Input     : keyword = "", limit = 5
     * Expected  : drugRepository.suggestNames gọi với keyword=""
     * CheckDB   : Yes — verify suggestNames gọi
     */
    @Test
    @DisplayName("TC-DRUG-08: suggestNames keyword rỗng → vẫn gọi repository")
    void suggestNames_emptyKeyword_callsRepository() {
        // Arrange
        given(drugRepository.suggestNames(eq(""), any()))
                .willReturn(List.of());

        // Act
        List<String> result = drugService.suggestNames("", 5);

        // Assert
        assertThat(result).isNotNull();
        verify(drugRepository).suggestNames(eq(""), any(Pageable.class));
    }
}
