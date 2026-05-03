package com.example.AuthService.controller;

import com.example.AuthService.dto.DrugFilter;
import com.example.AuthService.dto.response.DrugResponse;
import com.example.AuthService.security.jwt.JwtService;
import com.example.AuthService.service.DrugService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = DrugController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("DrugController (API / MockMvc)")
class DrugControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DrugService drugService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    // TC-API-DRUG-01
    @Test
    @DisplayName("GET /api/drugs phân trang (khách → isAdmin=false)")
    void listDrugs() throws Exception {
        var page = new PageImpl<>(
                List.of(DrugResponse.builder().id(1L).name("Paracetamol").build()),
                PageRequest.of(0, 20),
                1
        );
        when(drugService.getDrugs(any(DrugFilter.class), any(Pageable.class), eq(false)))
                .thenReturn(page);

        mockMvc.perform(get("/api/drugs").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Paracetamol"));

        verify(drugService).getDrugs(any(DrugFilter.class), any(Pageable.class), eq(false));
    }

    // TC-API-DRUG-02
    @Test
    @DisplayName("GET /api/drugs/suggest gợi ý tên")
    void suggest() throws Exception {
        when(drugService.suggestNames("pa", 10)).thenReturn(List.of("Paracetamol"));

        mockMvc.perform(get("/api/drugs/suggest").param("q", "pa").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Paracetamol"));

        verify(drugService).suggestNames("pa", 10);
    }
}
