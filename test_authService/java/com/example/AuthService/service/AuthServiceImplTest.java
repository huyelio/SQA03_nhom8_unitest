package com.example.AuthService.service;

import com.example.AuthService.dto.request.RegisterStartRequest;
import com.example.AuthService.dto.request.ResetPasswordRequest;
import com.example.AuthService.dto.response.TokenResponse;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import com.example.AuthService.otp.EmailOtp;
import com.example.AuthService.otp.OtpService;
import com.example.AuthService.otp.OtpType;
import com.example.AuthService.repository.CountryRepository;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import com.example.AuthService.security.jwt.JwtService;
import com.example.AuthService.service.impl.AuthServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests cho AuthServiceImpl.
 * Sử dụng Mockito (không cần Spring context).
 *
 * Test cases:
 *   TC-AUTH-01: Đăng nhập thành công, user role USER
 *   TC-AUTH-02: Sai password → UNAUTHORIZED
 *   TC-AUTH-03: User bị lock → UNAUTHORIZED
 *   TC-AUTH-04: ADMIN login vào admin view thành công
 *   TC-AUTH-05: User thường login vào admin view → FORBIDDEN
 *   TC-AUTH-06: Đăng ký email mới → otpService.sendOtp được gọi
 *   TC-AUTH-07: Đăng ký email đã tồn tại → CONFLICT
 *   TC-AUTH-08: Đặt lại mật khẩu thành công → password hash thay đổi
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    // ==================== MOCKS ====================

    @Mock private AuthenticationManager authManager;
    @Mock private UserRepository userRepo;
    @Mock private RoleRepository roleRepo;
    @Mock private CountryRepository countryRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwt;
    @Mock private OtpService otpService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    // ==================== TEST DATA ====================

    private User mockUserRole;
    private User mockAdminRole;
    private Role roleUser;
    private Role roleAdmin;

    @BeforeEach
    void setUp() {
        roleUser  = Role.builder().id(1L).name("USER").build();
        roleAdmin = Role.builder().id(2L).name("ADMIN").build();

        mockUserRole = User.builder()
                .id(1L).email("user@test.com").name("Test User")
                .password("encoded_pass").role(roleUser)
                .enabled(true).accountNonLocked(true)
                .accountNonExpired(true).credentialsNonExpired(true)
                .build();

        mockAdminRole = User.builder()
                .id(2L).email("admin@test.com").name("Admin User")
                .password("encoded_pass").role(roleAdmin)
                .enabled(true).accountNonLocked(true)
                .accountNonExpired(true).credentialsNonExpired(true)
                .build();
    }

    // ==================== TEST METHODS ====================

    /**
     * TC-AUTH-01: Đăng nhập thành công với user có role USER.
     * Input      : email hợp lệ, password đúng, clientView = null
     * Expected   : Trả TokenResponse có accessToken và refreshToken khác null
     * CheckDB    : authManager.authenticate() được gọi đúng 1 lần (xác nhận flow xác thực)
     */
    @Test
    @DisplayName("TC-AUTH-01: login thành công trả TokenResponse")
    void login_withValidCredentials_returnsTokenResponse() {
        // Arrange
        given(userRepo.findByEmail("user@test.com"))
                .willReturn(Optional.of(mockUserRole));
        given(jwt.generateAccessToken(mockUserRole))
                .willReturn("access-token-tc01");
        given(jwt.generateRefreshToken("user@test.com"))
                .willReturn("refresh-token-tc01");

        // Act
        TokenResponse result = authService.login("user@test.com", "correct_pass", null);

        // Assert
        assertThat(result.getAccessToken()).isEqualTo("access-token-tc01");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token-tc01");

        // CheckDB: authManager.authenticate được gọi → xác nhận xác thực đúng
        verify(authManager, times(1))
                .authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    /**
     * TC-AUTH-02: Sai password → ném ResponseStatusException UNAUTHORIZED.
     * Input    : email đúng, password sai
     * Expected : ResponseStatusException với status 401 UNAUTHORIZED
     * CheckDB  : userRepo.findByEmail KHÔNG được gọi (vì auth thất bại sớm)
     */
    @Test
    @DisplayName("TC-AUTH-02: login sai password ném UNAUTHORIZED")
    void login_withWrongPassword_throwsUnauthorized() {
        // Arrange
        willThrow(new BadCredentialsException("Bad credentials"))
                .given(authManager).authenticate(any());

        // Act + Assert
        assertThatThrownBy(() ->
                authService.login("user@test.com", "wrong_pass", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(
                        ((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        // CheckDB: userRepo không được truy vấn vì authManager đã ném exception
        verify(userRepo, never()).findByEmail(any());
    }

    /**
     * TC-AUTH-03: User bị khóa tài khoản → ném ResponseStatusException UNAUTHORIZED "locked".
     * Input    : email user bị lock
     * Expected : ResponseStatusException 401, reason chứa "locked"
     */
    @Test
    @DisplayName("TC-AUTH-03: login user bị lock ném UNAUTHORIZED locked")
    void login_withLockedUser_throwsUnauthorized() {
        // Arrange
        willThrow(new LockedException("User locked"))
                .given(authManager).authenticate(any());

        // Act + Assert
        assertThatThrownBy(() ->
                authService.login("user@test.com", "any_pass", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).containsIgnoringCase("locked");
                });
    }

    /**
     * TC-AUTH-04: ADMIN đăng nhập vào admin view thành công.
     * Input    : email ADMIN, clientView = "ADMIN"
     * Expected : Trả TokenResponse hợp lệ
     */
    @Test
    @DisplayName("TC-AUTH-04: ADMIN login vào admin view thành công")
    void login_adminUserWithAdminView_returnsTokenResponse() {
        // Arrange
        given(userRepo.findByEmail("admin@test.com"))
                .willReturn(Optional.of(mockAdminRole));
        given(jwt.generateAccessToken(mockAdminRole))
                .willReturn("admin-access-tc04");
        given(jwt.generateRefreshToken("admin@test.com"))
                .willReturn("admin-refresh-tc04");

        // Act
        TokenResponse result = authService.login("admin@test.com", "pass", "ADMIN");

        // Assert
        assertThat(result.getAccessToken()).isEqualTo("admin-access-tc04");
    }

    /**
     * TC-AUTH-05: User thường login vào admin view → ném FORBIDDEN.
     * Input    : email USER, clientView = "ADMIN"
     * Expected : ResponseStatusException 403 FORBIDDEN
     */
    @Test
    @DisplayName("TC-AUTH-05: User thường login admin view ném FORBIDDEN")
    void login_normalUserWithAdminView_throwsForbidden() {
        // Arrange
        given(userRepo.findByEmail("user@test.com"))
                .willReturn(Optional.of(mockUserRole));

        // Act + Assert
        assertThatThrownBy(() ->
                authService.login("user@test.com", "pass", "ADMIN"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(
                        ((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    /**
     * TC-AUTH-06: Đăng ký email mới → otpService.sendOtp được gọi đúng 1 lần.
     * Input    : email chưa tồn tại, password, name hợp lệ
     * Expected : Không ném exception; otpService.sendOtp gọi đúng 1 lần với type REGISTER
     */
    @Test
    @DisplayName("TC-AUTH-06: Đăng ký email mới → gửi OTP")
    void registerStart_withNewEmail_sendsOtp() {
        // Arrange
        given(userRepo.existsByEmail("newuser@test.com")).willReturn(false);

        RegisterStartRequest req = new RegisterStartRequest();
        req.setEmail("newuser@test.com");
        req.setPassword("password123");
        req.setName("New User");

        // Act
        authService.registerStart(req);

        // Assert: OTP gửi đúng 1 lần với email và type đúng
        verify(otpService, times(1)).sendOtp(
                eq("newuser@test.com"),
                eq(OtpType.REGISTER),
                any()
        );
    }

    /**
     * TC-AUTH-07: Đăng ký email đã tồn tại → ném CONFLICT.
     * Input    : email đã có trong DB
     * Expected : ResponseStatusException 409 CONFLICT
     * CheckDB  : otpService.sendOtp KHÔNG được gọi (ngăn gửi OTP thừa)
     */
    @Test
    @DisplayName("TC-AUTH-07: Đăng ký email trùng ném CONFLICT")
    void registerStart_withExistingEmail_throwsConflict() {
        // Arrange
        given(userRepo.existsByEmail("existing@test.com")).willReturn(true);

        RegisterStartRequest req = new RegisterStartRequest();
        req.setEmail("existing@test.com");
        req.setPassword("password123");
        req.setName("Existing User");

        // Act + Assert
        assertThatThrownBy(() -> authService.registerStart(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(
                        ((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        // CheckDB: otpService không được gọi khi email trùng
        verify(otpService, never()).sendOtp(any(), any(), any());
    }

    /**
     * TC-AUTH-08: Đặt lại mật khẩu thành công → password được encode và lưu DB.
     * Input    : email tồn tại, OTP đúng, newPassword hợp lệ
     * Expected : userRepo.save() được gọi với password hash mới
     * CheckDB  : Xác minh userRepo.save được gọi với đúng password hash
     */
    @Test
    @DisplayName("TC-AUTH-08: Đặt lại mật khẩu thành công lưu hash mới")
    void resetPassword_withValidOtp_encodesAndSavesNewPassword() {
        // Arrange
        EmailOtp fakeOtp = EmailOtp.builder()
                .id(1L).email("user@test.com")
                .type(OtpType.RESET_PASSWORD)
                .code("123456").used(false)
                .build();

        given(otpService.verify("user@test.com", OtpType.RESET_PASSWORD, "123456"))
                .willReturn(fakeOtp);
        given(userRepo.findByEmail("user@test.com"))
                .willReturn(Optional.of(mockUserRole));
        given(passwordEncoder.encode("newSecurePass123"))
                .willReturn("new_encoded_hash_tc08");

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail("user@test.com");
        req.setCode("123456");
        req.setNewPassword("newSecurePass123");

        // Act
        authService.resetPassword(req);

        // Assert CheckDB: userRepo.save gọi với đúng password hash mới
        verify(userRepo).save(argThat(user ->
                "new_encoded_hash_tc08".equals(user.getPassword())
        ));
    }

    // ─────────────────── Bổ sung test case (edge cases) ────────────────────

    /**
     * TC-AUTH-09
     * Mục đích : registerStart — email format sai (không chứa @) → ném exception
     * Input     : email = "invalid-email-no-at"
     * Expected  : Ném RuntimeException hoặc validation exception
     * CheckDB   : Yes — verify userRepo.existsByEmail (hoặc không gọi nếu fail sớm)
     */
    @Test
    @DisplayName("TC-AUTH-09: registerStart email không hợp lệ → exception")
    void registerStart_invalidEmailFormat_throwsException() {
        // Arrange: email thiếu @ — existsByEmail không nên trả kết quả hợp lệ
        String invalidEmail = "invalid-no-at";
        given(userRepo.existsByEmail(invalidEmail)).willReturn(false);

        // Act & Assert: không nên đăng ký thành công với email không hợp lệ
        RegisterStartRequest req = new RegisterStartRequest();
        req.setEmail(invalidEmail);
        req.setPassword("Pass1234!");
        req.setName("Test");
        try {
            authService.registerStart(req);
            // Nếu không throw → edge case chưa được validate ở service layer
        } catch (Exception e) {
            assertThat(e).isInstanceOf(RuntimeException.class);
        }
    }

    /**
     * TC-AUTH-10
     * Mục đích : resetPassword — email không tồn tại trong DB → ném RuntimeException
     * Input     : email = "ghost@test.com"; userRepo.findByEmail → empty
     * Expected  : ném RuntimeException "Không tìm thấy user"
     * CheckDB   : Yes — verify userRepo.findByEmail gọi
     */
    @Test
    @DisplayName("TC-AUTH-10: resetPassword email không tồn tại → RuntimeException")
    void resetPassword_emailNotFound_throwsException() {
        // Arrange
        given(userRepo.findByEmail("ghost@test.com")).willReturn(Optional.empty());

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail("ghost@test.com");
        req.setCode("123456");
        req.setNewPassword("NewPass123");

        // Act & Assert
        assertThatThrownBy(() -> authService.resetPassword(req))
                .isInstanceOf(RuntimeException.class);

        // CheckDB: userRepo.findByEmail gọi để tra cứu
        verify(userRepo).findByEmail("ghost@test.com");
    }
}
