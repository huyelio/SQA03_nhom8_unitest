package com.example.AuthService.controller;

import com.example.AuthService.dto.AuthProfileDto;
import com.example.AuthService.dto.request.ForgotPasswordRequest;
import com.example.AuthService.dto.request.OtpResendRequest;
import com.example.AuthService.dto.request.OtpVerifyRequest;
import com.example.AuthService.dto.request.RegisterStartRequest;
import com.example.AuthService.dto.request.ResetPasswordRequest;
import com.example.AuthService.dto.response.TokenResponse;
import com.example.AuthService.security.jwt.JwtService;
import com.example.AuthService.service.AuthProfileService;
import com.example.AuthService.service.AuthService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test lớp web {@link AuthController} bằng {@link MockMvc} (slice test, không cần MySQL).
 * Security filter tắt để tập trung mapping + validation + gọi service.
 */
@WebMvcTest(
        controllers = AuthController.class,
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
@DisplayName("AuthController (API / MockMvc)")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private AuthProfileService authProfileService;

    /** Cho {@code JwtAuthFilter} (component-scan); MockMvc tắt filter nên không gọi. */
    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    // Test Case ID: TC-API-AUTH-01
    @Test
    @DisplayName("POST /api/auth/login trả JSON token và ủy quyền authService")
    void loginReturnsTokenJson() throws Exception {
        when(authService.login("u@test.com", "secret", "ADMIN"))
                .thenReturn(new TokenResponse("acc", "ref"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"u@test.com","password":"secret","clientView":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("acc"))
                .andExpect(jsonPath("$.refreshToken").value("ref"));

        verify(authService).login("u@test.com", "secret", "ADMIN");
    }

    // Test Case ID: TC-API-AUTH-02
    @Test
    @DisplayName("POST /api/auth/refresh đọc refreshToken từ body")
    void refreshPostsBody() throws Exception {
        when(authService.refresh("rt-1")).thenReturn(new TokenResponse("a2", "rt-1"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"rt-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a2"));

        verify(authService).refresh("rt-1");
    }

    // Test Case ID: TC-API-AUTH-03
    @Test
    @DisplayName("POST /api/auth/register/start gọi service và 200")
    void registerStartOk() throws Exception {
        doNothing().when(authService).registerStart(any(RegisterStartRequest.class));

        mockMvc.perform(post("/api/auth/register/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"n@n.com\",\"password\":\"password123\",\"name\":\"N\"}"))
                .andExpect(status().isOk());

        verify(authService).registerStart(any(RegisterStartRequest.class));
    }

    // Test Case ID: TC-API-AUTH-04
    @Test
    @DisplayName("POST /api/auth/register/verify với @Valid")
    void registerVerifyValidBody() throws Exception {
        when(authService.registerVerify(any(OtpVerifyRequest.class)))
                .thenReturn(new TokenResponse("x", "y"));

        mockMvc.perform(post("/api/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@a.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("x"));

        verify(authService).registerVerify(any(OtpVerifyRequest.class));
    }

    // Test Case ID: TC-API-AUTH-05
    @Test
    @DisplayName("POST /api/auth/forgot-password trả 204")
    void forgotPasswordNoContent() throws Exception {
        doNothing().when(authService).forgotPassword(any(ForgotPasswordRequest.class));

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"b@b.com\"}"))
                .andExpect(status().isNoContent());

        verify(authService).forgotPassword(any(ForgotPasswordRequest.class));
    }

    // Test Case ID: TC-API-AUTH-06
    @Test
    @DisplayName("POST /api/auth/reset-password trả 204")
    void resetPasswordNoContent() throws Exception {
        doNothing().when(authService).resetPassword(any(ResetPasswordRequest.class));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"c@c.com","code":"999999","newPassword":"abcdefgh"}
                                """))
                .andExpect(status().isNoContent());

        verify(authService).resetPassword(any(ResetPasswordRequest.class));
    }

    // Test Case ID: TC-API-AUTH-07
    @Test
    @DisplayName("GET /api/auth/login/google redirect 302 tới OAuth2")
    void loginGoogleRedirects() throws Exception {
        mockMvc.perform(get("/api/auth/login/google"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/oauth2/authorization/google"));
    }

    // Test Case ID: TC-API-AUTH-08
    @Test
    @DisplayName("GET /api/auth/me trả profile (principal có thể null khi tắt filter)")
    void meReturnsProfile() throws Exception {
        AuthProfileDto dto = AuthProfileDto.builder()
                .authenticated(false)
                .provider("none")
                .build();
        when(authProfileService.buildProfile(any(), eq(true))).thenReturn(dto);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));

        verify(authProfileService).buildProfile(any(), eq(true));
    }

    // Test Case ID: TC-API-AUTH-09
    @Test
    @DisplayName("POST /api/auth/otp/resend gọi resendOtp")
    void resendOtpOk() throws Exception {
        doNothing().when(authService).resendOtp(any(OtpResendRequest.class));

        mockMvc.perform(post("/api/auth/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"d@d.com\",\"type\":\"REGISTER\"}"))
                .andExpect(status().isOk());

        verify(authService).resendOtp(any(OtpResendRequest.class));
    }
}
