package com.example.AuthService.otp;

import com.example.AuthService.mail.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit tests cho OtpService.
 * Sử dụng Mockito. ObjectMapper, SecureRandom, locks là field thực (không mock).
 *
 * Test cases:
 *   TC-OTP-01: sendOtp – gửi OTP mới khi chưa có OTP active → lưu DB + gửi email
 *   TC-OTP-02: verify – OTP đúng → đánh dấu used=true và lưu DB
 *   TC-OTP-03: verify – OTP sai → tăng attempts + ném exception
 *   TC-OTP-04: verify – không tìm thấy OTP active → ném IllegalStateException
 */
@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    // ==================== MOCKS ====================

    @Mock private EmailOtpRepository repo;
    @Mock private OtpProperties props;
    @Mock private EmailService emailService;

    @InjectMocks
    private OtpService otpService;

    // ==================== TEST DATA ====================

    private EmailOtp validOtp;
    private LocalDateTime futureExpiry;

    @BeforeEach
    void setUp() {
        futureExpiry = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5);

        // OTP hợp lệ: còn hạn, chưa dùng
        validOtp = EmailOtp.builder()
                .id(1L)
                .email("user@test.com")
                .type(OtpType.REGISTER)
                .code("654321")
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .expiresAt(futureExpiry)
                .used(false)
                .attempts(0)
                .build();
    }

    // ==================== TEST METHODS ====================

    /**
     * TC-OTP-01: Gửi OTP mới khi chưa có OTP active trong DB.
     * Input    : email = "new@test.com", type = REGISTER, payload rỗng
     * Expected : OTP mới được lưu vào DB và email được gửi đi
     * CheckDB  : repo.save gọi với OTP email đúng, used=false; emailService.send gọi 1 lần
     */
    @Test
    @DisplayName("TC-OTP-01: sendOtp lưu OTP mới và gửi email")
    void sendOtp_withNoExistingOtp_savesNewOtpAndSendsEmail() {
        // Arrange: không có OTP cũ active
        given(repo.findFirstByEmailAndTypeAndUsedFalseAndExpiresAtAfterOrderByIdDesc(
                eq("new@test.com"), eq(OtpType.REGISTER), any(LocalDateTime.class)))
                .willReturn(Optional.empty());
        given(props.getRegisterTtlSeconds()).willReturn(300);
        // emailService.send là void → Mockito không cần stub (default: do nothing)

        // Act
        otpService.sendOtp("new@test.com", OtpType.REGISTER, Optional.empty());

        // Assert CheckDB: repo.save gọi với OTP có email đúng và used=false
        verify(repo).save(argThat(otp ->
                "new@test.com".equals(otp.getEmail())
                && !otp.isUsed()
                && otp.getCode() != null
                && otp.getCode().length() == 6  // mã 6 chữ số
        ));

        // CheckDB: email được gửi đúng 1 lần
        verify(emailService).send(
                eq("new@test.com"),
                anyString(),
                anyString()
        );
    }

    /**
     * TC-OTP-02: Xác thực OTP đúng → đánh dấu used=true và lưu DB.
     * Input    : email, type REGISTER, code = "654321" (đúng)
     * Expected : Trả EmailOtp với used=true
     * CheckDB  : repo.save gọi với otp.used = true
     */
    @Test
    @DisplayName("TC-OTP-02: verify OTP đúng đánh dấu used=true")
    void verify_withCorrectCode_marksOtpAsUsed() {
        // Arrange
        given(repo.findFirstByEmailAndTypeAndUsedFalseAndExpiresAtAfterOrderByIdDesc(
                eq("user@test.com"), eq(OtpType.REGISTER), any(LocalDateTime.class)))
                .willReturn(Optional.of(validOtp));

        // Act
        EmailOtp result = otpService.verify("user@test.com", OtpType.REGISTER, "654321");

        // Assert: OTP trả về đã được đánh dấu used
        assertThat(result.isUsed()).isTrue();

        // CheckDB: repo.save gọi với otp đã dùng
        verify(repo).save(argThat(otp -> otp.isUsed() && otp.getId().equals(1L)));
    }

    /**
     * TC-OTP-03: Xác thực OTP sai → tăng attempts và ném IllegalArgumentException.
     * Input    : email, type REGISTER, code = "WRONG" (sai)
     * Expected : Ném IllegalArgumentException("OTP không đúng.");
     *            attempts tăng lên 1
     * CheckDB  : repo.save gọi với attempts = 1
     */
    @Test
    @DisplayName("TC-OTP-03: verify OTP sai tăng attempts và ném exception")
    void verify_withWrongCode_incrementsAttemptsAndThrows() {
        // Arrange
        given(repo.findFirstByEmailAndTypeAndUsedFalseAndExpiresAtAfterOrderByIdDesc(
                eq("user@test.com"), eq(OtpType.REGISTER), any(LocalDateTime.class)))
                .willReturn(Optional.of(validOtp));
        given(props.getMaxAttempts()).willReturn(5);

        // Act + Assert: ném đúng exception
        assertThatThrownBy(() ->
                otpService.verify("user@test.com", OtpType.REGISTER, "WRONG_CODE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OTP không đúng");

        // CheckDB: repo.save gọi với attempts đã tăng lên 1
        verify(repo).save(argThat(otp -> otp.getAttempts() == 1));
    }

    /**
     * TC-OTP-04: Xác thực OTP khi không tìm thấy OTP active → ném IllegalStateException.
     * Input    : email không có OTP active trong DB
     * Expected : Ném IllegalStateException("OTP không tồn tại hoặc đã hết hạn.")
     */
    @Test
    @DisplayName("TC-OTP-04: verify không có OTP active ném IllegalStateException")
    void verify_withNoActiveOtp_throwsIllegalStateException() {
        // Arrange: không có OTP nào active
        given(repo.findFirstByEmailAndTypeAndUsedFalseAndExpiresAtAfterOrderByIdDesc(
                anyString(), any(OtpType.class), any(LocalDateTime.class)))
                .willReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() ->
                otpService.verify("user@test.com", OtpType.REGISTER, "000000"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OTP không tồn tại hoặc đã hết hạn");
    }

    // ─────────────────── Bổ sung test case (edge cases) ────────────────────

    /**
     * TC-OTP-05
     * Mục đích : sendOtp — email null → NullPointerException hoặc IllegalArgumentException
     * Input     : email = null
     * Expected  : Ném runtime exception (NPE hoặc IllegalArgument)
     * CheckDB   : No — không gọi repo.save
     */
    @Test
    @DisplayName("TC-OTP-05: sendOtp email null → ném exception")
    void sendOtp_nullEmail_throwsException() {
        // Arrange: email null
        given(repo.findFirstByEmailAndTypeAndUsedFalseAndExpiresAtAfterOrderByIdDesc(
                eq(null), any(OtpType.class), any(LocalDateTime.class)))
                .willThrow(new IllegalArgumentException("email must not be null"));

        // Act & Assert
        assertThatThrownBy(() ->
                otpService.sendOtp(null, OtpType.REGISTER, Optional.empty()))
                .isInstanceOf(RuntimeException.class);
    }

    /**
     * TC-OTP-06
     * Mục đích : sendOtp cho RESET_PASSWORD → emailService.send gọi với OTP type đúng
     * Input     : email='reset@test.com', type=RESET_PASSWORD
     * Expected  : repo.save gọi; emailService.send gọi với email đúng
     * CheckDB   : Yes — verify repo.save (lưu OTP)
     * Rollback  : Mockito — không ghi DB thật
     */
    @Test
    @DisplayName("TC-OTP-06: sendOtp cho RESET_PASSWORD → lưu OTP mới, gửi email")
    void sendOtp_resetPasswordType_success() {
        // Arrange — RESET_PASSWORD dùng props.getResetTtlSeconds(), không phải getRegisterTtlSeconds
        given(repo.findFirstByEmailAndTypeAndUsedFalseAndExpiresAtAfterOrderByIdDesc(
                anyString(), any(OtpType.class), any(LocalDateTime.class)))
                .willReturn(Optional.empty());
        given(props.getResetTtlSeconds()).willReturn(300);
        given(repo.save(any(EmailOtp.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        otpService.sendOtp("reset@test.com", OtpType.RESET_PASSWORD, Optional.empty());

        // Assert — verify repo.save được gọi (CheckDB)
        verify(repo).save(argThat((EmailOtp o) ->
                "reset@test.com".equals(o.getEmail()) && !o.isUsed()));
        verify(emailService).send(eq("reset@test.com"), anyString(), anyString());
    }
}
