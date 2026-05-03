package com.example.AuthService.service.impl;

import com.example.AuthService.dto.request.ImportInvoiceDetailRequest;
import com.example.AuthService.dto.request.ImportInvoiceRequest;
import com.example.AuthService.dto.response.ImportInvoiceResponse;
import com.example.AuthService.entity.Drug;
import com.example.AuthService.entity.ImportInvoice;
import com.example.AuthService.entity.ImportInvoiceDetail;
import com.example.AuthService.repository.DrugRepository;
import com.example.AuthService.repository.ImportInvoiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test {@link ImportInvoiceServiceImpl}. CheckDB: verify {@link ImportInvoiceRepository#save}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ImportInvoiceServiceImpl")
class ImportInvoiceServiceImplTest {

    @Mock
    private ImportInvoiceRepository importInvoiceRepository;
    @Mock
    private DrugRepository drugRepository;

    @InjectMocks
    private ImportInvoiceServiceImpl importInvoiceService;

    /** Test Case ID: TC-IMP-GETALL-01 */
    @Test
    @DisplayName("getAll uses findAll when keyword blank")
    void getAllUsesFindAllWhenKeywordBlank() {
        Page<ImportInvoice> page = new PageImpl<>(List.of());
        when(importInvoiceRepository.findAll(any(PageRequest.class))).thenReturn(page);
        Page<ImportInvoiceResponse> res = importInvoiceService.getAll("   ", PageRequest.of(0, 5));
        assertNotNull(res);
        verify(importInvoiceRepository).findAll(any(PageRequest.class));
        verify(importInvoiceRepository, never()).findByNameContainingIgnoreCase(anyString(), any());
    }

    /** Test Case ID: TC-IMP-GETALL-02 */
    @Test
    @DisplayName("getAll uses keyword search when keyword present")
    void getAllUsesKeywordSearch() {
        Page<ImportInvoice> page = new PageImpl<>(List.of());
        when(importInvoiceRepository.findByNameContainingIgnoreCase(eq("ab"), any(PageRequest.class))).thenReturn(page);
        importInvoiceService.getAll("ab", PageRequest.of(0, 5));
        verify(importInvoiceRepository).findByNameContainingIgnoreCase(eq("ab"), any(PageRequest.class));
    }

    /** Test Case ID: TC-IMP-CREATE-01 */
    @Test
    @DisplayName("create saves invoice with resolved drugs")
    void createSavesInvoiceWithResolvedDrugs() {
        Drug drug = Drug.builder().id(1L).name("Paracetamol").price(java.math.BigDecimal.ONE).importPrice(java.math.BigDecimal.ONE).build();
        when(drugRepository.findByNameIgnoreCase("Paracetamol")).thenReturn(Optional.of(drug));
        ImportInvoiceRequest request = new ImportInvoiceRequest();
        request.setName("Inv-1");
        ImportInvoiceDetailRequest d = new ImportInvoiceDetailRequest();
        d.setDrugName("Paracetamol");
        d.setQuantity(5);
        request.setDetails(List.of(d));
        when(importInvoiceRepository.save(any(ImportInvoice.class))).thenAnswer(inv -> {
            ImportInvoice invc = inv.getArgument(0);
            invc.setId(100L);
            return invc;
        });

        ImportInvoiceResponse response = importInvoiceService.create(request);

        assertEquals(100L, response.getId());
        verify(importInvoiceRepository).save(any(ImportInvoice.class));
    }

    /** Test Case ID: TC-IMP-GET-01 */
    @Test
    @DisplayName("getById maps response")
    void getByIdMapsResponse() {
        Drug drug = Drug.builder().id(2L).name("D").price(java.math.BigDecimal.ONE).importPrice(java.math.BigDecimal.ONE).build();
        ImportInvoiceDetail detail = new ImportInvoiceDetail();
        detail.setId(20L);
        detail.setDrug(drug);
        detail.setQuantity(3);
        ImportInvoice invoice = new ImportInvoice();
        invoice.setId(7L);
        invoice.setName("N");
        invoice.setCreatedAt(LocalDateTime.now());
        invoice.setDetails(List.of(detail));
        when(importInvoiceRepository.findById(7L)).thenReturn(Optional.of(invoice));

        ImportInvoiceResponse res = importInvoiceService.getById(7L);

        assertEquals(7L, res.getId());
        assertEquals(1, res.getDetails().size());
        assertEquals(3, res.getDetails().get(0).getQuantity());
    }

    /** Test Case ID: TC-IMP-DEL-01 */
    @Test
    @DisplayName("delete delegates to repository")
    void deleteDelegates() {
        importInvoiceService.delete(9L);
        verify(importInvoiceRepository).deleteById(9L);
    }
}
