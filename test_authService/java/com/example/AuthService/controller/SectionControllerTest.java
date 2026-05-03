package com.example.AuthService.controller;

import com.example.AuthService.entity.Section;
import com.example.AuthService.security.jwt.JwtService;
import com.example.AuthService.service.SectionService;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = SectionController.class,
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
@DisplayName("SectionController (API / MockMvc)")
class SectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SectionService sectionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    // TC-API-SEC-01
    @Test
    @DisplayName("GET /api/drugs/{drugId}/sections trả mảng section")
    void listByDrug() throws Exception {
        Section s = new Section(1L, "A", "body", null);
        when(sectionService.listByDrug(5L)).thenReturn(List.of(s));

        mockMvc.perform(get("/api/drugs/5/sections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("A"));

        verify(sectionService).listByDrug(5L);
    }

    // TC-API-SEC-02
    @Test
    @DisplayName("POST /api/drugs/{drugId}/sections tạo section")
    void createSection() throws Exception {
        Section created = new Section(2L, "T", "C", null);
        when(sectionService.create(eq(1L), any(Section.class))).thenReturn(created);

        mockMvc.perform(post("/api/drugs/1/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"content\":\"C\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));

        verify(sectionService).create(eq(1L), any(Section.class));
    }

    // TC-API-SEC-03
    @Test
    @DisplayName("GET /api/sections/{id} trả một section")
    void getOne() throws Exception {
        when(sectionService.getById(9L)).thenReturn(new Section(9L, "x", "y", null));

        mockMvc.perform(get("/api/sections/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("x"));

        verify(sectionService).getById(9L);
    }

    // TC-API-SEC-04
    @Test
    @DisplayName("PUT /api/sections/{id} cập nhật")
    void updateSection() throws Exception {
        when(sectionService.update(eq(3L), any(Section.class)))
                .thenReturn(new Section(3L, "nt", "nc", null));

        mockMvc.perform(put("/api/sections/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"nt\",\"content\":\"nc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("nt"));

        verify(sectionService).update(eq(3L), any(Section.class));
    }

    // TC-API-SEC-05
    @Test
    @DisplayName("DELETE /api/sections/{id} trả 204")
    void deleteSection() throws Exception {
        doNothing().when(sectionService).delete(7L);

        mockMvc.perform(delete("/api/sections/7"))
                .andExpect(status().isNoContent());

        verify(sectionService).delete(7L);
    }

    // TC-API-SEC-06
    @Test
    @DisplayName("GET /api/sections listAll")
    void listAll() throws Exception {
        when(sectionService.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/sections"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(sectionService).listAll();
    }
}
