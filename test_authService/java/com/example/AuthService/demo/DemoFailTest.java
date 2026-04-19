package com.example.AuthService.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DemoFailTest — Test cases INTENTIONALLY FAILING for demonstration purposes.
 *
 * Mục đích : Chứng minh rằng testing pipeline phát hiện được lỗi thực sự.
 *            Các test này cố tình SAI để báo cáo có ví dụ FAIL (kết quả Fail).
 *
 * KHÔNG thay đổi các test này để chúng pass.
 * Chỉ dùng cho mục đích demo báo cáo.
 *
 * Test cases:
 *   TC-FAIL-01: Tổng tiền đơn hàng — assertion sai expected value — INTENTIONALLY FAILING
 *   TC-FAIL-02: Validation input — đòi string không rỗng nhưng input là "" — INTENTIONALLY FAILING
 */
class DemoFailTest {

    /**
     * TC-FAIL-01
     * Mục đích : Minh họa test FAIL — assertion sai về tổng tiền đơn hàng
     *
     * This test is intentionally failing for demonstration.
     * Lý do fail : unitPrice=50_000, qty=2 → actual total = 100_000
     *              nhưng assertion đòi total = 200_000 (expected sai cố tình)
     *
     * Input      : unitPrice = 50_000; quantity = 2
     * Expected   : total == 200_000  ← SAI CỐ TÌNH (thực tế là 100_000)
     * Kết quả    : AssertionError — expected 200000 but was 100000
     */
    @Test
    @DisplayName("TC-FAIL-01 [INTENTIONAL FAIL]: Tổng tiền order — expected value sai")
    void orderTotal_intentionallyWrongExpected() {
        // This test is intentionally failing for demonstration

        // Arrange
        BigDecimal unitPrice = new BigDecimal("50000");
        int quantity = 2;

        // Act — logic đúng: 50000 * 2 = 100000
        BigDecimal actualTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

        // Assert — CỐ TÌNH SAI: expected 200_000 nhưng thực tế là 100_000
        assertThat(actualTotal)
                .as("TC-FAIL-01: This assertion is intentionally wrong for demo "
                    + "— unitPrice=50000, qty=2 → actual=100000, but we expect 200000 (wrong)")
                .isEqualByComparingTo(new BigDecimal("200000")); // ← SAI CỐ TÌNH
    }

    /**
     * TC-FAIL-02
     * Mục đích : Minh họa test FAIL — kiểm tra validation chưa được implement
     *
     * This test is intentionally failing for demonstration.
     * Lý do fail : code hiện tại không reject empty email ở tầng pure logic
     *              → test đòi string không rỗng nhưng input là ""
     *
     * Input      : email = "" (empty string)
     * Expected   : email phải không rỗng (assertThat("").isNotEmpty())  ← SAI CỐ TÌNH
     * Kết quả    : AssertionError — expected not empty but was ""
     */
    @Test
    @DisplayName("TC-FAIL-02 [INTENTIONAL FAIL]: Email rỗng — edge case chưa xử lý")
    void emptyEmail_validationEdgeCase_intentionalFail() {
        // This test is intentionally failing for demonstration
        // Chứng minh edge case: email rỗng "" phải bị reject nhưng chưa được validate

        String emptyEmail = ""; // input không hợp lệ

        // Assert — CỐ TÌNH SAI: đòi not-empty nhưng "" là empty
        assertThat(emptyEmail)
                .as("TC-FAIL-02: This assertion is intentionally wrong for demo "
                    + "— empty email should be rejected, but service layer does not validate yet")
                .isNotEmpty(); // ← SAI CỐ TÌNH: "" sẽ fail isNotEmpty()
    }
}
