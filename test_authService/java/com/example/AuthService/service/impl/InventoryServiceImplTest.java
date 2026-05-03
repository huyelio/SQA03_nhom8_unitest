package com.example.AuthService.service.impl;

import com.example.AuthService.entity.Drug;
import com.example.AuthService.repository.DrugRepository;
import com.example.AuthService.repository.ImportInvoiceDetailRepository;
import com.example.AuthService.repository.OrderItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test {@link InventoryServiceImpl}. CheckDB: mock repo — xác minh công thức tồn kho qua {@link DrugRepository}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryServiceImpl")
class InventoryServiceImplTest {

    @Mock
    private ImportInvoiceDetailRepository importInvoiceDetailRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private DrugRepository drugRepository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    /** Test Case ID: TC-INV-STOCK-01 */
    @Test
    @DisplayName("calculateStock returns imported minus sold")
    void calculateStockReturnsImportedMinusSold() {
        when(importInvoiceDetailRepository.totalImported(10L)).thenReturn(100);
        Drug drug = Drug.builder().id(10L).soldQuantity(30).name("D").price(java.math.BigDecimal.ONE).importPrice(java.math.BigDecimal.ONE).build();
        when(drugRepository.findById(10L)).thenReturn(Optional.of(drug));

        assertEquals(70, inventoryService.calculateStock(10L));
    }

    /** Test Case ID: TC-INV-STOCK-02 */
    @Test
    @DisplayName("calculateStock throws when drug missing")
    void calculateStockThrowsWhenDrugMissing() {
        when(importInvoiceDetailRepository.totalImported(1L)).thenReturn(5);
        when(drugRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> inventoryService.calculateStock(1L));
    }

    /** Test Case ID: TC-INV-BATCH-01 */
    @Test
    @DisplayName("calculateStockForDrugs returns empty map (current impl)")
    void calculateStockForDrugsReturnsEmpty() {
        assertTrue(inventoryService.calculateStockForDrugs(List.of(1L, 2L)).isEmpty());
    }

    /** Test Case ID: TC-INV-IMPORTMAP-01 */
    @Test
    @DisplayName("getTotalImportedForDrugs maps query rows")
    void getTotalImportedForDrugsMapsRows() {
        when(importInvoiceDetailRepository.findTotalImportedForDrugs(List.of(1L, 2L)))
                .thenReturn(List.of(new Object[]{1L, 10L}, new Object[]{2L, 5L}));

        Map<Long, Integer> map = inventoryService.getTotalImportedForDrugs(List.of(1L, 2L));

        assertEquals(10, map.get(1L));
        assertEquals(5, map.get(2L));
    }
}
