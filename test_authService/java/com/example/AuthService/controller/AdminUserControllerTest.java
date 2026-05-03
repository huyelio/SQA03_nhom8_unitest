package com.example.AuthService.controller;

import com.example.AuthService.dto.request.UserUpdateRequestDTO;
import com.example.AuthService.dto.response.UserResponseDTO;
import com.example.AuthService.security.jwt.JwtService;
import com.example.AuthService.service.UserService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AdminUserController.class,
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
@DisplayName("AdminUserController (API / MockMvc)")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    // TC-API-ADMIN-01
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/users danh sách (mock)")
    void getAllUsers() throws Exception {
        when(userService.getAllUsers(0, 20, null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk());

        verify(userService).getAllUsers(0, 20, null, null, null);
    }

    // TC-API-ADMIN-02
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/users/{id} chi tiết user")
    void getUserById() throws Exception {
        UserResponseDTO dto = UserResponseDTO.builder().email("a@a.com").build();
        when(userService.getUserById(5L)).thenReturn(dto);

        mockMvc.perform(get("/api/admin/users/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("a@a.com"));

        verify(userService).getUserById(5L);
    }

    // TC-API-ADMIN-03
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /api/admin/users/{id} trả JSON status")
    void deleteUser() throws Exception {
        mockMvc.perform(delete("/api/admin/users/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        verify(userService).deleteUser(2L);
    }

    // TC-API-ADMIN-04
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/admin/users tạo user → 201")
    void createUser() throws Exception {
        UserResponseDTO created = UserResponseDTO.builder().email("new@n.com").build();
        when(userService.createUser(any(UserUpdateRequestDTO.class))).thenReturn(created);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@n.com\",\"name\":\"N\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@n.com"));

        verify(userService).createUser(any(UserUpdateRequestDTO.class));
    }
}
