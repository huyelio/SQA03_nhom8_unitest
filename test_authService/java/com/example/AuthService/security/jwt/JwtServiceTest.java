package com.example.AuthService.security.jwt;

import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests cho JwtService.
 * Không dùng Spring context — tạo JwtService thủ công với JwtProperties test.
 * initKey() gọi trực tiếp (package-private, cùng package với test).
 *
 * Test cases:
 *   TC-JWT-01: generateAccessToken – token chứa đúng subject, userId, roles
 *   TC-JWT-02: isValid – token mới tạo còn hợp lệ → true
 *   TC-JWT-03: isValid – token đã hết hạn → ném exception
 *   TC-JWT-04: extractUsername – trả đúng email lowercase
 */
class JwtServiceTest {

    // Base64 của "testSecretKeyForUnitTestingHmacSha256X" (41 bytes = 328 bits > 256 bits)
    private static final String TEST_SECRET_BASE64 =
            "dGVzdFNlY3JldEtleUZvclVuaXRUZXN0aW5nSG1hY1NoYTI1NlgK";

    private JwtService jwtService;
    private User mockUser;

    @BeforeEach
    void setUp() {
        // Tạo JwtProperties với secret test và expiry hợp lý
        JwtProperties props = new JwtProperties();
        props.setSecret(TEST_SECRET_BASE64);
        props.setAccessExpirationMs(3_600_000L);   // 1 giờ
        props.setRefreshExpirationMs(86_400_000L); // 24 giờ

        // Tạo JwtService thủ công và khởi tạo key (gọi @PostConstruct thủ công)
        jwtService = new JwtService(props);
        jwtService.initKey();  // package-private — truy cập được vì cùng package

        // User mock cho các test
        Role role = Role.builder().id(1L).name("USER").build();
        mockUser = User.builder()
                .id(42L).email("test@example.com").name("Test User")
                .password("pass").role(role)
                .enabled(true).accountNonLocked(true)
                .accountNonExpired(true).credentialsNonExpired(true)
                .build();
    }

    // ==================== TEST METHODS ====================

    /**
     * TC-JWT-01: generateAccessToken trả token chứa đúng subject và claims.
     * Input    : User hợp lệ với email "test@example.com", id = 42, role USER
     * Expected : Token decode ra subject = "test@example.com",
     *            claim "userId" = 42, claim "roles" chứa "ROLE_USER"
     */
    @Test
    @DisplayName("TC-JWT-01: generateAccessToken chứa đúng subject và claims")
    void generateAccessToken_withValidUser_containsCorrectClaims() {
        // Act
        String token = jwtService.generateAccessToken(mockUser);

        // Assert: token không rỗng
        assertThat(token).isNotBlank();

        // Decode token để kiểm tra claims
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET_BASE64));
        var claims = Jwts.parser()
                .verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("test@example.com");

        // Kiểm tra claim userId = 42
        assertThat(((Number) claims.get("userId")).longValue()).isEqualTo(42L);

        // Kiểm tra claim roles chứa "ROLE_USER"
        @SuppressWarnings("unchecked")
        var roles = (java.util.List<String>) claims.get("roles");
        assertThat(roles).contains("ROLE_USER");
    }

    /**
     * TC-JWT-02: isValid với token mới tạo (chưa hết hạn) → trả true.
     * Input    : Token vừa được generateRefreshToken
     * Expected : isValid = true
     */
    @Test
    @DisplayName("TC-JWT-02: isValid trả true cho token còn hạn")
    void isValid_withFreshToken_returnsTrue() {
        // Arrange
        String token = jwtService.generateRefreshToken("test@example.com");

        // Act + Assert
        assertThat(jwtService.isValid(token)).isTrue();
    }

    /**
     * TC-JWT-03: isValid với token hết hạn → ném exception.
     * Input    : Token có exp = 1 giây trước
     * Expected : Ném exception (JwtException hoặc subclass)
     */
    @Test
    @DisplayName("TC-JWT-03: isValid ném exception cho token hết hạn")
    void isValid_withExpiredToken_throwsException() {
        // Arrange: tạo thủ công token đã hết hạn (exp = 1 giây trước)
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET_BASE64));
        Instant now = Instant.now();

        String expiredToken = Jwts.builder()
                .subject("expired@example.com")
                .issuedAt(Date.from(now.minusSeconds(3600)))
                .expiration(Date.from(now.minusSeconds(120))) // hết hạn 120s trước, vượt clock-skew 60s
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        // Act + Assert: isValid ném exception khi token hết hạn
        assertThatThrownBy(() -> jwtService.isValid(expiredToken))
                .isInstanceOf(Exception.class);
    }

    /**
     * TC-JWT-04: extractUsername trả đúng email đã lowercase.
     * Input    : Token với subject viết hoa một phần "USER@Example.COM"
     * Expected : extractUsername trả về "user@example.com" (lowercase)
     */
    @Test
    @DisplayName("TC-JWT-04: extractUsername trả email lowercase")
    void extractUsername_fromToken_returnsLowercaseEmail() {
        // Arrange: generateRefreshToken tự lowercase subject
        String token = jwtService.generateRefreshToken("USER@Example.COM");

        // Act
        String username = jwtService.extractUsername(token);

        // Assert
        assertThat(username).isEqualTo("user@example.com");
    }

    // ─────────────────── Bổ sung test case (edge cases) ────────────────────

    /**
     * TC-JWT-05
     * Mục đích : generateRefreshToken trả token có subject = email lowercase
     * Input     : email = "Admin@Test.COM"
     * Expected  : extractUsername(token) = "admin@test.com"
     */
    @Test
    @DisplayName("TC-JWT-05: generateRefreshToken subject là email lowercase")
    void generateRefreshToken_subjectIsLowercase() {
        // Act
        String token = jwtService.generateRefreshToken("Admin@Test.COM");
        String subject = jwtService.extractUsername(token);

        // Assert
        assertThat(subject).isEqualTo("admin@test.com");
    }

    /**
     * TC-JWT-06
     * Mục đích : extractUsername — token tạo cho user1 trả subject = user1.id (không phải user2.id)
     * Input     : token tạo cho user1 (id=1)
     * Expected  : extractUsername trả "1", không trả "2"
     */
    @Test
    @DisplayName("TC-JWT-06: extractUsername không trả id của user khác")
    void extractUsername_returnsCorrectIdNotOtherUser() {
        // Arrange
        Role role = new Role(); role.setId(1L); role.setName("USER");
        User user1 = User.builder().id(1L).email("user1@test.com").role(role).build();

        // Act
        String token = jwtService.generateAccessToken(user1);
        String subject = jwtService.extractUsername(token);

        // Assert: subject là email của user1
        assertThat(subject).isEqualTo("user1@test.com");
        assertThat(subject).isNotEqualTo("user2@test.com");
    }
}
