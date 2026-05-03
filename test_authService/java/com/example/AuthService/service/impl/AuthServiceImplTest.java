package com.example.AuthService.service.impl;

import com.example.AuthService.dto.request.*;
import com.example.AuthService.dto.response.TokenResponse;
import com.example.AuthService.entity.Country;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import com.example.AuthService.otp.EmailOtp;
import com.example.AuthService.otp.OtpService;
import com.example.AuthService.otp.OtpType;
import com.example.AuthService.repository.CountryRepository;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import com.example.AuthService.security.jwt.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthServiceImpl} — lớp triển khai {@link com.example.AuthService.service.AuthService}.
 * <p>
 * <strong>CheckDB / Rollback (mock):</strong> Mọi truy cập JPA ({@link com.example.AuthService.repository.UserRepository},
 * {@link com.example.AuthService.repository.RoleRepository}, {@link com.example.AuthService.repository.CountryRepository})
 * đều là mock. “Kiểm tra DB” được thay bằng {@link ArgumentCaptor} + {@code verify(...)} để xác minh
 * tham số gọi {@code save}/{@code findById} đúng kỳ vọng (tương đương assert trên dữ liệu sẽ ghi xuống DB).
 * Không có kết nối DB thật → sau mỗi test không cần rollback vật lý; {@link #tearDown()} reset mock để test độc lập.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl unit tests")
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authManager;
    @Mock
    private UserRepository userRepo;
    @Mock
    private RoleRepository roleRepo;
    @Mock
    private CountryRepository countryRepo;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwt;
    @Mock
    private OtpService otpService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AuthServiceImpl authService;

    private static final String EMAIL = "user@example.com";
    private static final String EMAIL_NORMALIZED = "user@example.com";

    @BeforeEach
    void setUp() {
        lenient().when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "ENC(" + inv.getArgument(0, String.class) + ")");
    }

    @AfterEach
    void tearDown() {
        reset(authManager, userRepo, roleRepo, countryRepo, passwordEncoder, jwt, otpService);
    }

    private User buildUser(String roleName) {
        Role role = Role.builder().id(1L).name(roleName).build();
        return User.builder()
                .id(10L)
                .email(EMAIL_NORMALIZED)
                .name("Test User")
                .password("hash")
                .role(role)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }

    // --- login ---

    // Test Case ID: TC-LOGIN-01
    @Test
    @DisplayName("shouldReturnTokenWhenLoginSuccess")
    void shouldReturnTokenWhenLoginSuccess() throws Exception {
        User user = buildUser("USER");
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(Authentication.class));
        when(userRepo.findByEmail(EMAIL_NORMALIZED)).thenReturn(Optional.of(user));
        when(jwt.generateAccessToken(user)).thenReturn("access-jwt");
        when(jwt.generateRefreshToken(user.getUsername())).thenReturn("refresh-jwt");

        TokenResponse res = authService.login("  " + EMAIL + "  ", "secret", null);

        assertNotNull(res);
        assertEquals("access-jwt", res.getAccessToken());
        assertEquals("refresh-jwt", res.getRefreshToken());
        verify(authManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwt, times(1)).generateAccessToken(user);
        verify(jwt, times(1)).generateRefreshToken(EMAIL_NORMALIZED);
    }

    // Test Case ID: TC-LOGIN-02
    @Test
    @DisplayName("shouldThrowUnauthorizedWhenPasswordIsWrong")
    void shouldThrowUnauthorizedWhenPasswordIsWrong() {
        doThrow(new BadCredentialsException("bad")).when(authManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login(EMAIL, "wrong", null));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Bad credentials", ex.getReason());
        verify(userRepo, never()).findByEmail(anyString());
    }

    // Test Case ID: TC-LOGIN-03
    @Test
    @DisplayName("shouldThrowUnauthorizedWhenUserDisabled")
    void shouldThrowUnauthorizedWhenUserDisabled() {
        doThrow(new DisabledException("disabled")).when(authManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login(EMAIL, "p", null));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("User disabled", ex.getReason());
    }

    // Test Case ID: TC-LOGIN-04
    @Test
    @DisplayName("shouldThrowUnauthorizedWhenUserLocked")
    void shouldThrowUnauthorizedWhenUserLocked() {
        doThrow(new LockedException("locked")).when(authManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login(EMAIL, "p", null));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("User locked", ex.getReason());
    }

    // Test Case ID: TC-LOGIN-05
    @Test
    @DisplayName("shouldThrowForbiddenWhenAdminViewButUserIsNotAdmin")
    void shouldThrowForbiddenWhenAdminViewButUserIsNotAdmin() {
        User user = buildUser("USER");
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(Authentication.class));
        when(userRepo.findByEmail(EMAIL_NORMALIZED)).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login(EMAIL, "ok", "ADMIN"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Only ADMIN can login to admin view", ex.getReason());
        verify(jwt, never()).generateAccessToken(any());
    }

    // Test Case ID: TC-LOGIN-06
    @Test
    @DisplayName("shouldReturnTokenWhenAdminViewAndUserIsAdmin")
    void shouldReturnTokenWhenAdminViewAndUserIsAdmin() {
        User admin = buildUser("ADMIN");
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(Authentication.class));
        when(userRepo.findByEmail(EMAIL_NORMALIZED)).thenReturn(Optional.of(admin));
        when(jwt.generateAccessToken(admin)).thenReturn("a");
        when(jwt.generateRefreshToken(admin.getUsername())).thenReturn("r");

        TokenResponse res = authService.login(EMAIL, "ok", "ADMIN");

        assertEquals("a", res.getAccessToken());
        assertEquals("r", res.getRefreshToken());
    }

    // Test Case ID: TC-LOGIN-07
    @Test
    @DisplayName("shouldThrowUsernameNotFoundWhenUserMissingAfterAuthentication")
    void shouldThrowUsernameNotFoundWhenUserMissingAfterAuthentication() {
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(Authentication.class));
        when(userRepo.findByEmail(EMAIL_NORMALIZED)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> authService.login(EMAIL, "ok", null));
    }

    // --- refresh ---

    // Test Case ID: TC-REFRESH-01
    @Test
    @DisplayName("shouldReturnNewAccessWhenRefreshTokenValid")
    void shouldReturnNewAccessWhenRefreshTokenValid() {
        User user = buildUser("USER");
        String refresh = "refresh-token";
        when(jwt.isValid(refresh)).thenReturn(true);
        when(jwt.extractUsername(refresh)).thenReturn(EMAIL_NORMALIZED);
        when(userRepo.findByEmail(EMAIL_NORMALIZED)).thenReturn(Optional.of(user));
        when(jwt.generateAccessToken(user)).thenReturn("new-access");

        TokenResponse res = authService.refresh(refresh);

        assertEquals("new-access", res.getAccessToken());
        assertEquals(refresh, res.getRefreshToken());
        verify(jwt, times(1)).isValid(refresh);
        verify(jwt, times(1)).generateAccessToken(user);
    }

    // Test Case ID: TC-REFRESH-02
    @Test
    @DisplayName("shouldThrowBadRequestWhenRefreshTokenNull")
    void shouldThrowBadRequestWhenRefreshTokenNull() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.refresh(null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Missing refreshToken", ex.getReason());
        verify(jwt, never()).isValid(any());
    }

    // Test Case ID: TC-REFRESH-03
    @Test
    @DisplayName("shouldThrowUnauthorizedWhenRefreshTokenInvalid")
    void shouldThrowUnauthorizedWhenRefreshTokenInvalid() {
        when(jwt.isValid("bad")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.refresh("bad"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Invalid refresh token", ex.getReason());
    }

    // Test Case ID: TC-REFRESH-04
    @Test
    @DisplayName("shouldThrowUnauthorizedWhenUserNotFoundForRefresh")
    void shouldThrowUnauthorizedWhenUserNotFoundForRefresh() {
        when(jwt.isValid("t")).thenReturn(true);
        when(jwt.extractUsername("t")).thenReturn(EMAIL_NORMALIZED);
        when(userRepo.findByEmail(EMAIL_NORMALIZED)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.refresh("t"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("User not found", ex.getReason());
    }

    // --- registerStart ---

    // Test Case ID: TC-REG-START-01
    @Test
    @DisplayName("shouldThrowConflictWhenRegisterStartEmailAlreadyExists")
    void shouldThrowConflictWhenRegisterStartEmailAlreadyExists() {
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(true);
        RegisterStartRequest req = new RegisterStartRequest();
        req.setEmail(EMAIL);
        req.setPassword("password123");
        req.setName("N");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.registerStart(req));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Email Used", ex.getReason());
        verify(otpService, never()).sendOtp(anyString(), any(), any());
    }

    // Test Case ID: TC-REG-START-02
    @Test
    @DisplayName("shouldSendOtpWhenRegisterStartSuccess")
    void shouldSendOtpWhenRegisterStartSuccess() {
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(false);
        RegisterStartRequest req = new RegisterStartRequest();
        req.setEmail(EMAIL);
        req.setPassword("password123");
        req.setName("Alice");
        req.setGender("F");
        req.setPhoneNumber("090");
        req.setDateOfBirth(LocalDate.of(2000, 1, 2));
        req.setCountryId(5L);
        req.setPhotoUrl("http://p");

        authService.registerStart(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Optional<Map<String, Object>>> cap = ArgumentCaptor.forClass(Optional.class);
        verify(otpService, times(1)).sendOtp(eq(EMAIL_NORMALIZED), eq(OtpType.REGISTER), cap.capture());
        assertTrue(cap.getValue().isPresent());
        Map<String, Object> payload = cap.getValue().get();
        assertTrue(payload.get("passwordHash").toString().startsWith("ENC("));
        assertEquals("Alice", payload.get("name"));
        assertEquals("F", payload.get("gender"));
        assertEquals("090", payload.get("phoneNumber"));
        assertEquals("2000-01-02", payload.get("dateOfBirth"));
        assertEquals(5L, payload.get("countryId"));
        assertEquals("http://p", payload.get("photoUrl"));
        verify(passwordEncoder, times(1)).encode("password123");
    }

    /**
     * Test Case ID: TC-REG-START-03
     * <p>
     * Khi {@code dateOfBirth == null}, payload OTP phải chứa key {@code dateOfBirth} với giá trị {@code null}
     * (nhánh toString trong production).
     */
    @Test
    @DisplayName("shouldPutNullDateOfBirthInOtpPayloadWhenRegisterStartDateOfBirthNull")
    void shouldPutNullDateOfBirthInOtpPayloadWhenRegisterStartDateOfBirthNull() {
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(false);
        RegisterStartRequest registerStartRequest = new RegisterStartRequest();
        registerStartRequest.setEmail(EMAIL);
        registerStartRequest.setPassword("password123");
        registerStartRequest.setName("NoDob");
        registerStartRequest.setDateOfBirth(null);

        authService.registerStart(registerStartRequest);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Optional<Map<String, Object>>> otpPayloadCaptor = ArgumentCaptor.forClass(Optional.class);
        verify(otpService, times(1)).sendOtp(eq(EMAIL_NORMALIZED), eq(OtpType.REGISTER), otpPayloadCaptor.capture());
        Map<String, Object> capturedPayload = otpPayloadCaptor.getValue().orElseThrow();
        assertNull(capturedPayload.get("dateOfBirth"), "Null DOB must be stored explicitly for OTP round-trip");
    }

    /**
     * Test Case ID: TC-REG-START-04
     * <p>
     * Email null sau normalize → vẫn kiểm tra {@code existsByEmail(null)} và có thể gửi OTP (hành vi defensive / edge).
     */
    @Test
    @DisplayName("shouldCallOtpWithNullEmailWhenRegisterStartRequestEmailIsNull")
    void shouldCallOtpWithNullEmailWhenRegisterStartRequestEmailIsNull() {
        when(userRepo.existsByEmail(null)).thenReturn(false);
        RegisterStartRequest registerStartRequest = new RegisterStartRequest();
        registerStartRequest.setEmail(null);
        registerStartRequest.setPassword("password123");
        registerStartRequest.setName("NullEmail");

        authService.registerStart(registerStartRequest);

        verify(userRepo, times(1)).existsByEmail(null);
        verify(otpService, times(1)).sendOtp(isNull(), eq(OtpType.REGISTER), any());
    }

    // --- registerVerify ---

    private String registerPayloadJson() throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("passwordHash", "ENC(stored)");
        m.put("name", "Bob");
        m.put("gender", "M");
        m.put("phoneNumber", "091");
        m.put("dateOfBirth", "1999-05-05");
        m.put("countryId", 1L);
        m.put("photoUrl", "u");
        return objectMapper.writeValueAsString(m);
    }

    // Test Case ID: TC-REG-VERIFY-01
    @Test
    @DisplayName("shouldCreateUserAndReturnTokensWhenRegisterVerifySuccess")
    void shouldCreateUserAndReturnTokensWhenRegisterVerifySuccess() throws Exception {
        String json = registerPayloadJson();
        EmailOtp otp = EmailOtp.builder().id(1L).payloadJson(json).build();
        when(otpService.verify(EMAIL_NORMALIZED, OtpType.REGISTER, "123456")).thenReturn(otp);
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(false);
        Role userRole = Role.builder().id(2L).name("USER").build();
        when(roleRepo.findByName("USER")).thenReturn(Optional.of(userRole));
        Country country = new Country(1L, "VN", "VN");
        when(countryRepo.findById(1L)).thenReturn(Optional.of(country));

        User saved = User.builder().id(99L).email(EMAIL_NORMALIZED).name("Bob").role(userRole).build();
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwt.generateAccessToken(any(User.class))).thenReturn("acc");
        when(jwt.generateRefreshToken(EMAIL_NORMALIZED)).thenReturn("ref");

        OtpVerifyRequest req = new OtpVerifyRequest();
        req.setEmail(EMAIL);
        req.setCode("123456");

        TokenResponse res = authService.registerVerify(req);

        assertEquals("acc", res.getAccessToken());
        assertEquals("ref", res.getRefreshToken());

        // CheckDB (mock): đảm bảo entity lưu khớp payload + quan hệ
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo, times(1)).save(userCaptor.capture());
        User persistedUserAfterSave = userCaptor.getValue();
        assertEquals(EMAIL_NORMALIZED, persistedUserAfterSave.getEmail());
        assertEquals("Bob", persistedUserAfterSave.getName());
        assertEquals("ENC(stored)", persistedUserAfterSave.getPassword());
        assertEquals(country, persistedUserAfterSave.getCountry());
        assertEquals(userRole, persistedUserAfterSave.getRole());
        assertTrue(persistedUserAfterSave.isEnabled());
        // Rollback/Cleanup: không có DB thật — tearDown() reset mock
    }

    /**
     * Test Case ID: TC-REG-VERIFY-06
     * <p>
     * CheckDB (mock): khi role USER chưa tồn tại, service phải {@code save} role mới rồi gán cho user.
     */
    @Test
    @DisplayName("shouldCreateUserRoleWhenUserRoleMissingInDatabase")
    void shouldCreateUserRoleWhenUserRoleMissingInDatabase() throws Exception {
        Map<String, Object> registrationPayload = new HashMap<>();
        registrationPayload.put("passwordHash", "ENC(x)");
        registrationPayload.put("name", "RoleSeed");
        String payloadJson = objectMapper.writeValueAsString(registrationPayload);
        EmailOtp verifiedOtp = EmailOtp.builder().payloadJson(payloadJson).build();
        when(otpService.verify(EMAIL_NORMALIZED, OtpType.REGISTER, "777")).thenReturn(verifiedOtp);
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(false);
        when(roleRepo.findByName("USER")).thenReturn(Optional.empty());
        Role newlyCreatedRole = Role.builder().id(44L).name("USER").build();
        when(roleRepo.save(any(Role.class))).thenReturn(newlyCreatedRole);
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.generateAccessToken(any(User.class))).thenReturn("acc2");
        when(jwt.generateRefreshToken(EMAIL_NORMALIZED)).thenReturn("ref2");

        OtpVerifyRequest otpVerifyRequest = new OtpVerifyRequest();
        otpVerifyRequest.setEmail(EMAIL);
        otpVerifyRequest.setCode("777");

        authService.registerVerify(otpVerifyRequest);

        ArgumentCaptor<Role> roleSaveCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepo, times(1)).save(roleSaveCaptor.capture());
        assertEquals("USER", roleSaveCaptor.getValue().getName());

        ArgumentCaptor<User> userSaveCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo, times(1)).save(userSaveCaptor.capture());
        assertEquals(newlyCreatedRole, userSaveCaptor.getValue().getRole());
    }

    /**
     * Test Case ID: TC-REG-VERIFY-07
     * <p>
     * Payload không có {@code countryId} → không gọi {@code countryRepo.findById}; user.country == null.
     */
    @Test
    @DisplayName("shouldPersistUserWithoutCountryWhenCountryIdAbsentFromOtpPayload")
    void shouldPersistUserWithoutCountryWhenCountryIdAbsentFromOtpPayload() throws Exception {
        Map<String, Object> registrationPayload = new HashMap<>();
        registrationPayload.put("passwordHash", "h");
        registrationPayload.put("name", "NoCountry");
        String payloadJson = objectMapper.writeValueAsString(registrationPayload);
        EmailOtp verifiedOtp = EmailOtp.builder().payloadJson(payloadJson).build();
        when(otpService.verify(EMAIL_NORMALIZED, OtpType.REGISTER, "1")).thenReturn(verifiedOtp);
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(false);
        when(roleRepo.findByName("USER")).thenReturn(Optional.of(Role.builder().id(1L).name("USER").build()));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.generateAccessToken(any(User.class))).thenReturn("a");
        when(jwt.generateRefreshToken(anyString())).thenReturn("r");

        OtpVerifyRequest otpVerifyRequest = new OtpVerifyRequest();
        otpVerifyRequest.setEmail(EMAIL);
        otpVerifyRequest.setCode("1");

        authService.registerVerify(otpVerifyRequest);

        verify(countryRepo, never()).findById(anyLong());
        ArgumentCaptor<User> userSaveCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo, times(1)).save(userSaveCaptor.capture());
        assertNull(userSaveCaptor.getValue().getCountry());
    }

    /**
     * Test Case ID: TC-REG-VERIFY-08
     * <p>
     * {@code countryId} là chuỗi rỗng → parseLong trả null → không lookup country (nhánh an toàn).
     */
    @Test
    @DisplayName("shouldSkipCountryLookupWhenCountryIdStringIsBlank")
    void shouldSkipCountryLookupWhenCountryIdStringIsBlank() throws Exception {
        Map<String, Object> registrationPayload = new HashMap<>();
        registrationPayload.put("passwordHash", "h");
        registrationPayload.put("name", "BlankCountry");
        registrationPayload.put("countryId", "");
        String payloadJson = objectMapper.writeValueAsString(registrationPayload);
        EmailOtp verifiedOtp = EmailOtp.builder().payloadJson(payloadJson).build();
        when(otpService.verify(EMAIL_NORMALIZED, OtpType.REGISTER, "1")).thenReturn(verifiedOtp);
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(false);
        when(roleRepo.findByName("USER")).thenReturn(Optional.of(Role.builder().name("USER").build()));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.generateAccessToken(any(User.class))).thenReturn("a");
        when(jwt.generateRefreshToken(anyString())).thenReturn("r");

        OtpVerifyRequest otpVerifyRequest = new OtpVerifyRequest();
        otpVerifyRequest.setEmail(EMAIL);
        otpVerifyRequest.setCode("1");

        authService.registerVerify(otpVerifyRequest);

        verify(countryRepo, never()).findById(anyLong());
    }

    /**
     * Test Case ID: TC-REG-VERIFY-09
     * <p>
     * {@code dateOfBirth} null trong JSON → {@link User#getDateOfBirth()} null sau save.
     */
    @Test
    @DisplayName("shouldPersistNullDateOfBirthWhenOtpPayloadOmitsOrNullsDob")
    void shouldPersistNullDateOfBirthWhenOtpPayloadOmitsOrNullsDob() throws Exception {
        Map<String, Object> registrationPayload = new HashMap<>();
        registrationPayload.put("passwordHash", "h");
        registrationPayload.put("name", "NoDob");
        registrationPayload.put("dateOfBirth", null);
        String payloadJson = objectMapper.writeValueAsString(registrationPayload);
        EmailOtp verifiedOtp = EmailOtp.builder().payloadJson(payloadJson).build();
        when(otpService.verify(EMAIL_NORMALIZED, OtpType.REGISTER, "1")).thenReturn(verifiedOtp);
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(false);
        when(roleRepo.findByName("USER")).thenReturn(Optional.of(Role.builder().name("USER").build()));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.generateAccessToken(any(User.class))).thenReturn("a");
        when(jwt.generateRefreshToken(anyString())).thenReturn("r");

        OtpVerifyRequest otpVerifyRequest = new OtpVerifyRequest();
        otpVerifyRequest.setEmail(EMAIL);
        otpVerifyRequest.setCode("1");

        authService.registerVerify(otpVerifyRequest);

        ArgumentCaptor<User> userSaveCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo, times(1)).save(userSaveCaptor.capture());
        assertNull(userSaveCaptor.getValue().getDateOfBirth());
    }

    /**
     * Test Case ID: TC-REG-VERIFY-10
     * <p>
     * {@code dateOfBirth} chuỗi rỗng → coi như không có ngày sinh (parse nhánh blank).
     */
    @Test
    @DisplayName("shouldPersistNullDateOfBirthWhenOtpPayloadDobStringIsBlank")
    void shouldPersistNullDateOfBirthWhenOtpPayloadDobStringIsBlank() throws Exception {
        Map<String, Object> registrationPayload = new HashMap<>();
        registrationPayload.put("passwordHash", "h");
        registrationPayload.put("name", "BlankDob");
        registrationPayload.put("dateOfBirth", "   ");
        String payloadJson = objectMapper.writeValueAsString(registrationPayload);
        EmailOtp verifiedOtp = EmailOtp.builder().payloadJson(payloadJson).build();
        when(otpService.verify(EMAIL_NORMALIZED, OtpType.REGISTER, "1")).thenReturn(verifiedOtp);
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(false);
        when(roleRepo.findByName("USER")).thenReturn(Optional.of(Role.builder().name("USER").build()));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.generateAccessToken(any(User.class))).thenReturn("a");
        when(jwt.generateRefreshToken(anyString())).thenReturn("r");

        OtpVerifyRequest otpVerifyRequest = new OtpVerifyRequest();
        otpVerifyRequest.setEmail(EMAIL);
        otpVerifyRequest.setCode("1");

        authService.registerVerify(otpVerifyRequest);

        ArgumentCaptor<User> userSaveCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo, times(1)).save(userSaveCaptor.capture());
        assertNull(userSaveCaptor.getValue().getDateOfBirth());
    }

    /**
     * Test Case ID: TC-REG-VERIFY-11
     * <p>
     * Sau {@code readValue}, {@code countryId} thường là {@link Integer} (JSON number) → nhánh {@code Number} của parseLong.
     */
    @Test
    @DisplayName("shouldResolveCountryWhenCountryIdIsJsonNumberDeserializedAsInteger")
    void shouldResolveCountryWhenCountryIdIsJsonNumberDeserializedAsInteger() throws Exception {
        Map<String, Object> registrationPayload = new HashMap<>();
        registrationPayload.put("passwordHash", "h");
        registrationPayload.put("name", "IntCountry");
        registrationPayload.put("countryId", 7);
        String payloadJson = objectMapper.writeValueAsString(registrationPayload);
        EmailOtp verifiedOtp = EmailOtp.builder().payloadJson(payloadJson).build();
        when(otpService.verify(EMAIL_NORMALIZED, OtpType.REGISTER, "1")).thenReturn(verifiedOtp);
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(false);
        when(roleRepo.findByName("USER")).thenReturn(Optional.of(Role.builder().name("USER").build()));
        Country countryEntity = new Country(7L, "TestLand", "TL");
        when(countryRepo.findById(7L)).thenReturn(Optional.of(countryEntity));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.generateAccessToken(any(User.class))).thenReturn("a");
        when(jwt.generateRefreshToken(anyString())).thenReturn("r");

        OtpVerifyRequest otpVerifyRequest = new OtpVerifyRequest();
        otpVerifyRequest.setEmail(EMAIL);
        otpVerifyRequest.setCode("1");

        authService.registerVerify(otpVerifyRequest);

        ArgumentCaptor<User> userSaveCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo, times(1)).save(userSaveCaptor.capture());
        assertEquals(countryEntity, userSaveCaptor.getValue().getCountry());
        verify(countryRepo, times(1)).findById(7L);
    }

    /**
     * Test Case ID: TC-REG-VERIFY-12
     * <p>
     * {@code countryId} là chuỗi số hợp lệ → nhánh {@code String} của {@code parseLong} trong implementation.
     */
    @Test
    @DisplayName("shouldResolveCountryWhenCountryIdIsNumericStringInPayload")
    void shouldResolveCountryWhenCountryIdIsNumericStringInPayload() throws Exception {
        Map<String, Object> registrationPayload = new HashMap<>();
        registrationPayload.put("passwordHash", "h");
        registrationPayload.put("name", "StrCountry");
        registrationPayload.put("countryId", "2");
        String payloadJson = objectMapper.writeValueAsString(registrationPayload);
        EmailOtp verifiedOtp = EmailOtp.builder().payloadJson(payloadJson).build();
        when(otpService.verify(EMAIL_NORMALIZED, OtpType.REGISTER, "1")).thenReturn(verifiedOtp);
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(false);
        when(roleRepo.findByName("USER")).thenReturn(Optional.of(Role.builder().name("USER").build()));
        Country countryEntity = new Country(2L, "Land2", "L2");
        when(countryRepo.findById(2L)).thenReturn(Optional.of(countryEntity));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.generateAccessToken(any(User.class))).thenReturn("a");
        when(jwt.generateRefreshToken(anyString())).thenReturn("r");

        OtpVerifyRequest otpVerifyRequest = new OtpVerifyRequest();
        otpVerifyRequest.setEmail(EMAIL);
        otpVerifyRequest.setCode("1");

        authService.registerVerify(otpVerifyRequest);

        ArgumentCaptor<User> userSaveCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo, times(1)).save(userSaveCaptor.capture());
        assertEquals(countryEntity, userSaveCaptor.getValue().getCountry());
    }

    /**
     * Test Case ID: TC-REG-VERIFY-13
     * <p>
     * {@code countryId} không phải {@link Number} cũng không phải chuỗi số → {@code parseLong} trả {@code null},
     * không gọi {@code countryRepo.findById} (nhánh fallback của parseLong).
     */
    @Test
    @DisplayName("shouldIgnoreCountryWhenCountryIdInPayloadIsNonNumericType")
    void shouldIgnoreCountryWhenCountryIdInPayloadIsNonNumericType() throws Exception {
        Map<String, Object> registrationPayload = new HashMap<>();
        registrationPayload.put("passwordHash", "h");
        registrationPayload.put("name", "BadCountryType");
        registrationPayload.put("countryId", true);
        String payloadJson = objectMapper.writeValueAsString(registrationPayload);
        EmailOtp verifiedOtp = EmailOtp.builder().payloadJson(payloadJson).build();
        when(otpService.verify(EMAIL_NORMALIZED, OtpType.REGISTER, "1")).thenReturn(verifiedOtp);
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(false);
        when(roleRepo.findByName("USER")).thenReturn(Optional.of(Role.builder().name("USER").build()));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.generateAccessToken(any(User.class))).thenReturn("a");
        when(jwt.generateRefreshToken(anyString())).thenReturn("r");

        OtpVerifyRequest otpVerifyRequest = new OtpVerifyRequest();
        otpVerifyRequest.setEmail(EMAIL);
        otpVerifyRequest.setCode("1");

        authService.registerVerify(otpVerifyRequest);

        verify(countryRepo, never()).findById(anyLong());
        ArgumentCaptor<User> userSaveCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo, times(1)).save(userSaveCaptor.capture());
        assertNull(userSaveCaptor.getValue().getCountry());
    }

    // Test Case ID: TC-REG-VERIFY-02
    @Test
    @DisplayName("shouldThrowInternalErrorWhenOtpPayloadJsonInvalid")
    void shouldThrowInternalErrorWhenOtpPayloadJsonInvalid() {
        EmailOtp otp = EmailOtp.builder().payloadJson("not-json-at-all").build();
        when(otpService.verify(EMAIL_NORMALIZED, OtpType.REGISTER, "1")).thenReturn(otp);

        OtpVerifyRequest req = new OtpVerifyRequest();
        req.setEmail(EMAIL);
        req.setCode("1");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.registerVerify(req));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
        assertEquals("Invalid OTP payload", ex.getReason());
        verify(userRepo, never()).save(any());
    }

    // Test Case ID: TC-REG-VERIFY-03
    @Test
    @DisplayName("shouldThrowConflictWhenEmailAlreadyExistsAtVerify")
    void shouldThrowConflictWhenEmailAlreadyExistsAtVerify() throws Exception {
        String json = registerPayloadJson();
        EmailOtp otp = EmailOtp.builder().payloadJson(json).build();
        when(otpService.verify(EMAIL_NORMALIZED, OtpType.REGISTER, "1")).thenReturn(otp);
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(true);

        OtpVerifyRequest req = new OtpVerifyRequest();
        req.setEmail(EMAIL);
        req.setCode("1");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.registerVerify(req));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Email Used", ex.getReason());
        verify(userRepo, never()).save(any());
    }

    // Test Case ID: TC-REG-VERIFY-04
    @Test
    @DisplayName("shouldThrowBadRequestWhenCountryIdNotFound")
    void shouldThrowBadRequestWhenCountryIdNotFound() throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("passwordHash", "h");
        m.put("name", "N");
        m.put("countryId", 999L);
        String json = objectMapper.writeValueAsString(m);
        EmailOtp otp = EmailOtp.builder().payloadJson(json).build();
        when(otpService.verify(EMAIL_NORMALIZED, OtpType.REGISTER, "1")).thenReturn(otp);
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(false);
        when(roleRepo.findByName("USER")).thenReturn(Optional.of(Role.builder().name("USER").build()));
        when(countryRepo.findById(999L)).thenReturn(Optional.empty());

        OtpVerifyRequest req = new OtpVerifyRequest();
        req.setEmail(EMAIL);
        req.setCode("1");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.registerVerify(req));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Country không tồn tại", ex.getReason());
    }

    // Test Case ID: TC-REG-VERIFY-05
    @Test
    @DisplayName("shouldPropagateExceptionWhenOtpVerifyFails")
    void shouldPropagateExceptionWhenOtpVerifyFails() {
        when(otpService.verify(anyString(), eq(OtpType.REGISTER), anyString()))
                .thenThrow(new IllegalArgumentException("OTP không đúng."));

        OtpVerifyRequest req = new OtpVerifyRequest();
        req.setEmail(EMAIL);
        req.setCode("wrong");

        assertThrows(IllegalArgumentException.class, () -> authService.registerVerify(req));
    }

    // --- forgot / reset ---

    // Test Case ID: TC-FORGOT-01
    @Test
    @DisplayName("shouldThrowNotFoundWhenForgotPasswordEmailMissing")
    void shouldThrowNotFoundWhenForgotPasswordEmailMissing() {
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(false);
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail(EMAIL);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.forgotPassword(req));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Email not found", ex.getReason());
        verify(otpService, never()).sendOtp(anyString(), any(), any());
    }

    /**
     * Test Case ID: TC-FORGOT-02
     * <p>
     * CheckDB (mock): email tồn tại → chỉ gọi {@code existsByEmail} (đọc) và {@code sendOtp} RESET (không ghi user ở bước này).
     */
    @Test
    @DisplayName("shouldSendResetPasswordOtpWhenForgotPasswordEmailExists")
    void shouldSendResetPasswordOtpWhenForgotPasswordEmailExists() {
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(true);
        ForgotPasswordRequest forgotPasswordRequest = new ForgotPasswordRequest();
        forgotPasswordRequest.setEmail("  " + EMAIL + "  ");

        authService.forgotPassword(forgotPasswordRequest);

        verify(userRepo, times(1)).existsByEmail(EMAIL_NORMALIZED);
        verify(otpService, times(1)).sendOtp(EMAIL_NORMALIZED, OtpType.RESET_PASSWORD, Optional.empty());
    }

    // Test Case ID: TC-RESET-01
    @Test
    @DisplayName("shouldUpdatePasswordWhenResetPasswordSuccess")
    void shouldUpdatePasswordWhenResetPasswordSuccess() {
        User user = buildUser("USER");
        user.setPassword("old");
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail(EMAIL);
        req.setCode("999999");
        req.setNewPassword("newpass123");

        when(otpService.verify(EMAIL_NORMALIZED, OtpType.RESET_PASSWORD, "999999")).thenReturn(EmailOtp.builder().build());
        when(userRepo.findByEmail(EMAIL_NORMALIZED)).thenReturn(Optional.of(user));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.resetPassword(req);

        // CheckDB (mock): mật khẩu mới được encode và lưu
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo, times(1)).save(captor.capture());
        assertEquals("ENC(newpass123)", captor.getValue().getPassword());
        verify(passwordEncoder, times(1)).encode("newpass123");
        verify(otpService, times(1)).verify(EMAIL_NORMALIZED, OtpType.RESET_PASSWORD, "999999");
    }

    // Test Case ID: TC-RESET-02
    @Test
    @DisplayName("shouldThrowNotFoundWhenUserMissingAfterOtpReset")
    void shouldThrowNotFoundWhenUserMissingAfterOtpReset() {
        when(otpService.verify(EMAIL_NORMALIZED, OtpType.RESET_PASSWORD, "1")).thenReturn(EmailOtp.builder().build());
        when(userRepo.findByEmail(EMAIL_NORMALIZED)).thenReturn(Optional.empty());

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail(EMAIL);
        req.setCode("1");
        req.setNewPassword("newpass123");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.resetPassword(req));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("User not found", ex.getReason());
        verify(userRepo, never()).save(any());
    }

    // --- getByEmail ---

    // Test Case ID: TC-GETBY-01
    @Test
    @DisplayName("shouldReturnUserWhenGetByEmailExists")
    void shouldReturnUserWhenGetByEmailExists() {
        User user = buildUser("USER");
        when(userRepo.findByEmail(EMAIL_NORMALIZED)).thenReturn(Optional.of(user));

        User out = authService.getByEmailOrThrow(" " + EMAIL + " ");

        assertSame(user, out);
    }

    // Test Case ID: TC-GETBY-02
    @Test
    @DisplayName("shouldThrowNotFoundWhenGetByEmailMissing")
    void shouldThrowNotFoundWhenGetByEmailMissing() {
        when(userRepo.findByEmail(EMAIL_NORMALIZED)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.getByEmailOrThrow(EMAIL));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("User not found", ex.getReason());
    }

    /**
     * Test Case ID: TC-GETBY-03
     * <p>
     * Nhánh {@code normalize(null)} → {@code null} truyền xuống repository (không trim/toLowerCase).
     */
    @Test
    @DisplayName("shouldNormalizeNullEmailWhenGetByEmailOrThrowReceivesNull")
    void shouldNormalizeNullEmailWhenGetByEmailOrThrowReceivesNull() {
        when(userRepo.findByEmail(null)).thenReturn(Optional.empty());

        ResponseStatusException notFoundException = assertThrows(ResponseStatusException.class,
                () -> authService.getByEmailOrThrow(null));

        assertEquals(HttpStatus.NOT_FOUND, notFoundException.getStatusCode());
        verify(userRepo, times(1)).findByEmail(null);
    }

    // --- resendOtp ---

    // Test Case ID: TC-RESEND-01
    @Test
    @DisplayName("shouldResendOtpForRegisterWhenEmailFree")
    void shouldResendOtpForRegisterWhenEmailFree() {
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(false);
        OtpResendRequest req = new OtpResendRequest();
        req.setEmail(EMAIL);
        req.setType(OtpType.REGISTER);

        authService.resendOtp(req);

        verify(otpService, times(1)).sendOtp(EMAIL_NORMALIZED, OtpType.REGISTER, Optional.empty());
    }

    // Test Case ID: TC-RESEND-02
    @Test
    @DisplayName("shouldResendOtpForResetPasswordWhenEmailExists")
    void shouldResendOtpForResetPasswordWhenEmailExists() {
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(true);
        OtpResendRequest req = new OtpResendRequest();
        req.setEmail(EMAIL);
        req.setType(OtpType.RESET_PASSWORD);

        authService.resendOtp(req);

        verify(otpService, times(1)).sendOtp(EMAIL_NORMALIZED, OtpType.RESET_PASSWORD, Optional.empty());
    }

    // Test Case ID: TC-RESEND-03
    @Test
    @DisplayName("shouldThrowBadRequestWhenResendOtpTypeNull")
    void shouldThrowBadRequestWhenResendOtpTypeNull() {
        OtpResendRequest req = new OtpResendRequest();
        req.setEmail(EMAIL);
        req.setType(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.resendOtp(req));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("OtpType không được để trống", ex.getReason());
    }

    // Test Case ID: TC-RESEND-04
    @Test
    @DisplayName("shouldThrowConflictWhenResendRegisterButEmailUsed")
    void shouldThrowConflictWhenResendRegisterButEmailUsed() {
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(true);
        OtpResendRequest req = new OtpResendRequest();
        req.setEmail(EMAIL);
        req.setType(OtpType.REGISTER);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.resendOtp(req));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Email đã được sử dụng", ex.getReason());
        verify(otpService, never()).sendOtp(anyString(), any(), any());
    }

    // Test Case ID: TC-RESEND-05
    @Test
    @DisplayName("shouldThrowNotFoundWhenResendResetButEmailMissing")
    void shouldThrowNotFoundWhenResendResetButEmailMissing() {
        when(userRepo.existsByEmail(EMAIL_NORMALIZED)).thenReturn(false);
        OtpResendRequest req = new OtpResendRequest();
        req.setEmail(EMAIL);
        req.setType(OtpType.RESET_PASSWORD);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.resendOtp(req));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Email không tồn tại", ex.getReason());
    }

    /**
     * Test Case ID: TC-RESEND-06
     * <p>
     * {@code resendOtp} với email null: {@code normalize(null)} trong luồng resend (REGISTER, email chưa dùng).
     */
    @Test
    @DisplayName("shouldResendRegisterOtpWhenNormalizedEmailIsNullAndNotExists")
    void shouldResendRegisterOtpWhenNormalizedEmailIsNullAndNotExists() {
        when(userRepo.existsByEmail(null)).thenReturn(false);
        OtpResendRequest otpResendRequest = new OtpResendRequest();
        otpResendRequest.setEmail(null);
        otpResendRequest.setType(OtpType.REGISTER);

        authService.resendOtp(otpResendRequest);

        verify(otpService, times(1)).sendOtp(isNull(), eq(OtpType.REGISTER), eq(Optional.empty()));
    }
}
