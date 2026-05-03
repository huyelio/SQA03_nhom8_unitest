package com.example.AuthService.service.impl;

import com.example.AuthService.entity.Order;
import com.example.AuthService.entity.Payment;
import com.example.AuthService.entity.User;
import com.example.AuthService.enums.OrderStatus;
import com.example.AuthService.enums.PaymentMethod;
import com.example.AuthService.enums.PaymentStatus;
import com.example.AuthService.repository.DrugRepository;
import com.example.AuthService.repository.OrderRepository;
import com.example.AuthService.repository.PaymentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test {@link PaymentServiceImpl}. VNPay URL build dùng secret thật trong test — chỉ mock repository + request.
 * CheckDB: captor {@link PaymentRepository#save(Payment)}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl")
class PaymentServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private DrugRepository drugRepository;
    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @BeforeEach
    void injectVnpayConfiguration() {
        ReflectionTestUtils.setField(paymentService, "tmnCode", "TESTTMN");
        ReflectionTestUtils.setField(paymentService, "hashSecret",
                "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        ReflectionTestUtils.setField(paymentService, "payUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(paymentService, "returnUrl", "http://localhost/return");
        ReflectionTestUtils.setField(paymentService, "refundUrl", "http://localhost/refund");
    }

    private User userWithId(long id) {
        return User.builder().id(id).email("u@u.com").name("U").password("p")
                .enabled(true).accountNonExpired(true).accountNonLocked(true).credentialsNonExpired(true).build();
    }

    /** Test Case ID: TC-PAY-VNPAY-CREATE-01 */
    @Test
    @DisplayName("createVnPayPayment throws when order belongs to another user")
    void createVnPayPaymentThrowsWhenWrongUser() {
        Order order = Order.builder().id(1L).user(userWithId(2L)).totalAmount(BigDecimal.TEN).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> paymentService.createVnPayPayment(1L, userWithId(9L), httpServletRequest));
        assertTrue(ex.getMessage().contains("quyền"));
    }

    /** Test Case ID: TC-PAY-VNPAY-CREATE-02 */
    @Test
    @DisplayName("createVnPayPayment persists PENDING payment and returns URL with query")
    void createVnPayPaymentPersistsPaymentAndReturnsUrl() {
        User owner = userWithId(1L);
        Order order = Order.builder().id(10L).user(owner).totalAmount(BigDecimal.valueOf(50)).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        String payUrlResult = paymentService.createVnPayPayment(10L, owner, httpServletRequest);

        assertTrue(payUrlResult.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
        assertTrue(payUrlResult.contains("vnp_SecureHash="));
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals(PaymentStatus.PENDING, paymentCaptor.getValue().getStatus());
        assertEquals(PaymentMethod.VNPAY, paymentCaptor.getValue().getMethod());
        assertEquals(order, paymentCaptor.getValue().getOrder());
    }

    /** Test Case ID: TC-PAY-VNPAY-RETURN-01 */
    @Test
    @DisplayName("handleVnpayReturn returns false when signature missing")
    void handleVnpayReturnFalseWhenSignatureMissing() {
        assertFalse(paymentService.handleVnpayReturn(Map.of("vnp_TxnRef", "x")));
    }

    /** Test Case ID: TC-PAY-VNPAY-IPN-01 */
    @Test
    @DisplayName("handleVnpayIPN returns false when signature missing")
    void handleVnpayIpnFalseWhenSignatureMissing() {
        assertFalse(paymentService.handleVnpayIPN(new HashMap<>()));
    }

}
