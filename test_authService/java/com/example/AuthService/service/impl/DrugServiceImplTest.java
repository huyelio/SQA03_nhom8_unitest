package com.example.AuthService.service.impl;

import com.example.AuthService.dto.DrugFilter;
import com.example.AuthService.dto.response.DrugResponse;
import com.example.AuthService.entity.Drug;
import com.example.AuthService.repository.DrugRepository;
import com.example.AuthService.service.CloudinaryService;
import com.example.AuthService.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test {@link DrugServiceImpl}. CheckDB: verify {@link DrugRepository#save}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DrugServiceImpl")
class DrugServiceImplTest {

    @Mock
    private DrugRepository drugRepository;
    @Mock
    private CloudinaryService cloudinaryService;
    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private DrugServiceImpl drugService;

    /** Test Case ID: TC-DRUG-CREATE-01 */
    @Test
    @DisplayName("createDrug saves entity")
    void createDrugSavesEntity() {
        Drug drug = Drug.builder().name("X").price(BigDecimal.ONE).importPrice(BigDecimal.ONE).build();
        when(drugRepository.save(drug)).thenReturn(drug);
        assertSame(drug, drugService.createDrug(drug));
    }

    /** Test Case ID: TC-DRUG-DELETE-01 */
    @Test
    @DisplayName("deleteDrug throws when id unknown")
    void deleteDrugThrowsWhenUnknown() {
        when(drugRepository.existsById(1L)).thenReturn(false);
        assertThrows(RuntimeException.class, () -> drugService.deleteDrug(1L));
    }

    /** Test Case ID: TC-DRUG-PAGE-01 */
    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("getDrugs builds stock from inventory map")
    void getDrugsBuildsStockFromInventory() {
        Drug drug = Drug.builder().id(1L).name("N").title("T").price(BigDecimal.TEN).importPrice(BigDecimal.ONE)
                .soldQuantity(2).isActive(true).build();
        Page<Drug> page = new PageImpl<>(List.of(drug));
        when(drugRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(inventoryService.getTotalImportedForDrugs(List.of(1L))).thenReturn(Map.of(1L, 10));

        Page<DrugResponse> out = drugService.getDrugs(new DrugFilter(), PageRequest.of(0, 10, Sort.by("id")), true);

        assertEquals(1, out.getContent().size());
        assertEquals(8, out.getContent().get(0).getStockQuantity());
    }

    /** Test Case ID: TC-DRUG-SUGGEST-01 */
    @Test
    @DisplayName("suggestNames clamps limit")
    void suggestNamesClampsLimit() {
        when(drugRepository.suggestNames(eq("asp"), eq(PageRequest.of(0, 10)))).thenReturn(List.of("aspirin"));
        assertEquals(List.of("aspirin"), drugService.suggestNames("asp", 100));
    }

    /** Test Case ID: TC-DRUG-IMAGE-01 */
    @Test
    @DisplayName("createDrugWithImage uploads then saves")
    void createDrugWithImageUploadsThenSaves() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(cloudinaryService.uploadImage(file)).thenReturn("https://img");
        Drug drug = Drug.builder().name("I").price(BigDecimal.ONE).importPrice(BigDecimal.ONE).build();
        when(drugRepository.save(any(Drug.class))).thenAnswer(inv -> inv.getArgument(0));

        Drug saved = drugService.createDrugWithImage(drug, file);

        assertEquals("https://img", saved.getImage());
        verify(drugRepository).save(drug);
    }
}
