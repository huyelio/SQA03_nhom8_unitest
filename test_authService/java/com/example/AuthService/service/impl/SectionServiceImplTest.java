package com.example.AuthService.service.impl;

import com.example.AuthService.entity.Drug;
import com.example.AuthService.entity.Section;
import com.example.AuthService.repository.DrugRepository;
import com.example.AuthService.repository.SectionRepository;
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
import static org.mockito.Mockito.*;

/**
 * Unit test {@link SectionServiceImpl}. CheckDB: verify/captor trên {@link SectionRepository}/{@link DrugRepository} (mock).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SectionServiceImpl")
class SectionServiceImplTest {

    @Mock
    private SectionRepository sectionRepository;
    @Mock
    private DrugRepository drugRepository;

    @InjectMocks
    private SectionServiceImpl sectionService;

    /** Test Case ID: TC-SECTION-LIST-01 */
    @Test
    @DisplayName("listByDrug throws when drug missing")
    void listByDrugThrowsWhenDrugMissing() {
        when(drugRepository.existsById(9L)).thenReturn(false);
        assertThrows(RuntimeException.class, () -> sectionService.listByDrug(9L));
        verify(sectionRepository, never()).findByDrugIdOrderByIdAsc(anyLong());
    }

    /** Test Case ID: TC-SECTION-LIST-02 */
    @Test
    @DisplayName("listByDrug returns ordered sections when drug exists")
    void listByDrugReturnsSections() {
        when(drugRepository.existsById(1L)).thenReturn(true);
        List<Section> expected = List.of(new Section());
        when(sectionRepository.findByDrugIdOrderByIdAsc(1L)).thenReturn(expected);
        assertSame(expected, sectionService.listByDrug(1L));
    }

    /** Test Case ID: TC-SECTION-CREATE-01 */
    @Test
    @DisplayName("create persists section linked to drug from path")
    void createPersistsSectionLinkedToDrug() {
        Drug drug = Drug.builder().id(5L).name("Aspirin").price(java.math.BigDecimal.ONE).importPrice(java.math.BigDecimal.ONE).build();
        when(drugRepository.findById(5L)).thenReturn(Optional.of(drug));
        Section payload = new Section();
        payload.setTitle("T");
        payload.setContent("C");
        when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> inv.getArgument(0));

        Section saved = sectionService.create(5L, payload);

        ArgumentCaptor<Section> captor = ArgumentCaptor.forClass(Section.class);
        verify(sectionRepository).save(captor.capture());
        assertEquals("T", captor.getValue().getTitle());
        assertEquals(drug, captor.getValue().getDrug());
        assertSame(saved, captor.getValue());
    }

    /** Test Case ID: TC-SECTION-GET-01 */
    @Test
    @DisplayName("getById throws when section missing")
    void getByIdThrowsWhenMissing() {
        when(sectionRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> sectionService.getById(1L));
    }

    /** Test Case ID: TC-SECTION-UPDATE-01 */
    @Test
    @DisplayName("update merges title and content only")
    void updateMergesTitleAndContent() {
        Section existing = new Section();
        existing.setId(2L);
        existing.setTitle("old");
        when(sectionRepository.findById(2L)).thenReturn(Optional.of(existing));
        Section payload = new Section();
        payload.setTitle("new");
        when(sectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Section out = sectionService.update(2L, payload);

        assertEquals("new", out.getTitle());
        verify(sectionRepository).save(existing);
    }

    /** Test Case ID: TC-SECTION-DEL-01 */
    @Test
    @DisplayName("delete throws when section id unknown")
    void deleteThrowsWhenUnknown() {
        when(sectionRepository.existsById(3L)).thenReturn(false);
        assertThrows(RuntimeException.class, () -> sectionService.delete(3L));
        verify(sectionRepository, never()).deleteById(anyLong());
    }

    /** Test Case ID: TC-SECTION-DEL-02 */
    @Test
    @DisplayName("delete calls repository when id exists")
    void deleteCallsRepository() {
        when(sectionRepository.existsById(3L)).thenReturn(true);
        sectionService.delete(3L);
        verify(sectionRepository).deleteById(3L);
    }
}
