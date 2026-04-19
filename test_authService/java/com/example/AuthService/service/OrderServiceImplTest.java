package com.example.AuthService.service;

import com.example.AuthService.dto.request.CreateOrderRequest;
import com.example.AuthService.dto.request.OrderItemRequest;
import com.example.AuthService.entity.*;
import com.example.AuthService.enums.OrderStatus;
import com.example.AuthService.enums.PaymentMethod;
import com.example.AuthService.enums.PaymentStatus;
import com.example.AuthService.repository.DrugRepository;
import com.example.AuthService.repository.OrderRepository;
import com.example.AuthService.repository.PaymentRepository;
import com.example.AuthService.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho OrderServiceImpl.
 * Sử dụng Mockito — không gọi DB thật, không gọi VNPay thật.
 *
 * Test cases:
 *   TC-ORDER-01: createOrder thành công, 1 thuốc, đủ tồn kho
 *   TC-ORDER-02: createOrder — thuốc không tồn tại → RuntimeException
 *   TC-ORDER-03: createOrder — số lượng <= 0 → RuntimeException
 *   TC-ORDER-04: createOrder — tồn kho không đủ → RuntimeException
 *   TC-ORDER-05: confirmCodPayment thành công — PENDING → set COD
 *   TC-ORDER-06: confirmCodPayment — order không thuộc user → RuntimeException
 *   TC-ORDER-07: confirmCodPayment — order không ở PENDING → RuntimeException
 *   TC-ORDER-08: cancelOrder PENDING → CANCELLED, orderRepo.save gọi
 *   TC-ORDER-09: cancelOrder order không thuộc user → RuntimeException
 *   TC-ORDER-10: cancelOrder trạng thái không hợp lệ → RuntimeException
 *   TC-ORDER-11: approveRefund — VNPay refund thất bại → RuntimeException
 *   TC-ORDER-12: approveRefund — order không ở CANCEL_REQUESTED → RuntimeException
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRepository    orderRepository;
    @Mock private DrugRepository     drugRepository;
    @Mock private PaymentRepository  paymentRepository;
    @Mock private PaymentService     paymentService;
    @Mock private InventoryService   inventoryService;

    @InjectMocks
    private OrderServiceImpl orderService;

    /** User dùng chung trong các test */
    private User testUser;
    /** Admin dùng trong approve-refund test */
    private User adminUser;
    /** Drug đơn giản */
    private Drug drug;
    /** Role */
    private Role userRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        userRole = new Role();
        userRole.setId(1L);
        userRole.setName("USER");

        adminRole = new Role();
        adminRole.setId(2L);
        adminRole.setName("ADMIN");

        testUser = User.builder()
                .id(1L)
                .email("user@test.com")
                .role(userRole)
                .build();

        adminUser = User.builder()
                .id(99L)
                .email("admin@test.com")
                .role(adminRole)
                .build();

        drug = Drug.builder()
                .id(10L)
                .name("Paracetamol")
                .price(new BigDecimal("50000"))
                .soldQuantity(0)
                .reservedQuantity(0)
                .build();
    }

    // ─────────────────────────── createOrder ────────────────────────────

    /**
     * TC-ORDER-01
     * Mục đích: createOrder thành công với 1 thuốc, đủ tồn kho
     * Input    : CreateOrderRequest{items=[{drugId=10, qty=2}]}, user
     * Expected : orderRepository.save gọi 1 lần; order trả về != null
     * CheckDB  : Yes — verify orderRepository.save
     * Rollback : Mockito — không ghi DB thật
     */
    @Test
    @DisplayName("TC-ORDER-01: createOrder thành công, đủ tồn kho")
    void createOrder_success_sufficientStock() {
        // Arrange
        OrderItemRequest item = new OrderItemRequest();
        item.setDrugId(10L);
        item.setQuantity(2);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));
        request.setShippingAddress("123 Test St");
        request.setReceiverName("Nguyen Van A");
        request.setReceiverPhone("0912345678");

        given(drugRepository.findById(10L)).willReturn(Optional.of(drug));
        given(inventoryService.calculateStock(10L)).willReturn(10); // đủ tồn kho

        Order savedOrder = Order.builder()
                .id(1L)
                .user(testUser)
                .totalAmount(new BigDecimal("100000"))
                .status(OrderStatus.PENDING)
                .build();
        given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

        // Act
        Order result = orderService.createOrder(request, testUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        // CheckDB: xác minh orderRepository.save được gọi đúng 1 lần
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    /**
     * TC-ORDER-02
     * Mục đích: createOrder — drugId không tồn tại → RuntimeException
     * Input    : items=[{drugId=999}], drugRepo.findById → empty
     * Expected : RuntimeException "Không tìm thấy thuốc ID: 999"
     * CheckDB  : Yes — verify drugRepository.findById gọi
     */
    @Test
    @DisplayName("TC-ORDER-02: createOrder — thuốc không tồn tại → RuntimeException")
    void createOrder_drugNotFound_throwsException() {
        // Arrange
        OrderItemRequest item = new OrderItemRequest();
        item.setDrugId(999L);
        item.setQuantity(1);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        given(drugRepository.findById(999L)).willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderService.createOrder(request, testUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("999");

        // CheckDB: drugRepository.findById được gọi để tra cứu
        verify(drugRepository).findById(999L);
    }

    /**
     * TC-ORDER-03
     * Mục đích: createOrder — quantity <= 0 → RuntimeException "Số lượng không hợp lệ"
     * Input    : items=[{drugId=10, qty=0}]
     * Expected : RuntimeException "Số lượng không hợp lệ"
     * CheckDB  : Yes — drugRepository.findById gọi; orderRepository.save KHÔNG gọi
     */
    @Test
    @DisplayName("TC-ORDER-03: createOrder — số lượng = 0 → RuntimeException")
    void createOrder_invalidQuantity_throwsException() {
        // Arrange
        OrderItemRequest item = new OrderItemRequest();
        item.setDrugId(10L);
        item.setQuantity(0); // quantity không hợp lệ

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        given(drugRepository.findById(10L)).willReturn(Optional.of(drug));

        // Act & Assert
        assertThatThrownBy(() -> orderService.createOrder(request, testUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Số lượng không hợp lệ");

        // CheckDB: orderRepository.save KHÔNG được gọi (không lưu order lỗi)
        verify(orderRepository, never()).save(any());
    }

    /**
     * TC-ORDER-04
     * Mục đích: createOrder — tồn kho không đủ → RuntimeException
     * Input    : items=[{drugId=10, qty=100}]; inventoryService.calculateStock → 5
     * Expected : RuntimeException "Không đủ tồn kho"
     * CheckDB  : Yes — verify inventoryService.calculateStock
     */
    @Test
    @DisplayName("TC-ORDER-04: createOrder — tồn kho không đủ → RuntimeException")
    void createOrder_insufficientStock_throwsException() {
        // Arrange
        OrderItemRequest item = new OrderItemRequest();
        item.setDrugId(10L);
        item.setQuantity(100); // muốn 100 nhưng chỉ còn 5

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        given(drugRepository.findById(10L)).willReturn(Optional.of(drug));
        given(inventoryService.calculateStock(10L)).willReturn(5); // chỉ còn 5

        // Act & Assert
        assertThatThrownBy(() -> orderService.createOrder(request, testUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không đủ tồn kho");

        // CheckDB: inventoryService.calculateStock được gọi để kiểm tra kho
        verify(inventoryService).calculateStock(10L);
        verify(orderRepository, never()).save(any());
    }

    // ─────────────────────────── confirmCodPayment ──────────────────────────

    /**
     * TC-ORDER-05
     * Mục đích: confirmCodPayment thành công — order PENDING, đúng user
     * Input    : orderId=1, user hợp lệ; order.status=PENDING, paymentMethod=null
     * Expected : order.paymentMethod = COD; orderRepository.save gọi
     * CheckDB  : Yes — verify orderRepository.save với COD
     * Rollback : Mockito
     */
    @Test
    @DisplayName("TC-ORDER-05: confirmCodPayment thành công → set PaymentMethod.COD")
    void confirmCodPayment_success() {
        // Arrange
        OrderItem oi = new OrderItem();
        oi.setDrug(drug);
        oi.setQuantity(1);

        Order order = Order.builder()
                .id(1L)
                .user(testUser)
                .status(OrderStatus.PENDING)
                .items(List.of(oi))
                .build();
        order.setPaymentMethod(null); // chưa chọn phương thức

        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(orderRepository.save(any(Order.class))).willReturn(order);

        // Act
        Order result = orderService.confirmCodPayment(1L, testUser);

        // Assert
        assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.COD);
        // CheckDB: verify save được gọi sau khi set COD
        verify(orderRepository).save(order);
    }

    /**
     * TC-ORDER-06
     * Mục đích: confirmCodPayment — order không thuộc user hiện tại → RuntimeException
     * Input    : orderId=1; order.user.id=2 (khác testUser.id=1)
     * Expected : RuntimeException "Bạn không có quyền"
     */
    @Test
    @DisplayName("TC-ORDER-06: confirmCodPayment — user khác → RuntimeException")
    void confirmCodPayment_wrongUser_throwsException() {
        // Arrange
        User anotherUser = User.builder().id(2L).role(userRole).build();
        Order order = Order.builder()
                .id(1L)
                .user(anotherUser) // thuộc user khác
                .status(OrderStatus.PENDING)
                .build();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // Act & Assert
        assertThatThrownBy(() -> orderService.confirmCodPayment(1L, testUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("quyền");

        verify(orderRepository, never()).save(any());
    }

    /**
     * TC-ORDER-07
     * Mục đích: confirmCodPayment — order không ở PENDING → RuntimeException
     * Input    : order.status = PAID (đã thanh toán)
     * Expected : RuntimeException "Đơn hàng không ở trạng thái PENDING"
     */
    @Test
    @DisplayName("TC-ORDER-07: confirmCodPayment — order không ở PENDING → RuntimeException")
    void confirmCodPayment_notPending_throwsException() {
        // Arrange
        Order order = Order.builder()
                .id(1L)
                .user(testUser)
                .status(OrderStatus.PAID) // đã thanh toán, không phải PENDING
                .build();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // Act & Assert
        assertThatThrownBy(() -> orderService.confirmCodPayment(1L, testUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PENDING");

        verify(orderRepository, never()).save(any());
    }

    // ─────────────────────────── cancelOrder ───────────────────────────────

    /**
     * TC-ORDER-08
     * Mục đích: cancelOrder — order PENDING của đúng user → CANCELLED, save gọi
     * Input    : orderId=1, user hợp lệ; order.status=PENDING
     * Expected : order.status = CANCELLED; orderRepository.save gọi
     * CheckDB  : Yes — verify save với status CANCELLED
     * Rollback : Mockito
     */
    @Test
    @DisplayName("TC-ORDER-08: cancelOrder PENDING → CANCELLED")
    void cancelOrder_pending_success() {
        // Arrange
        Order order = Order.builder()
                .id(1L)
                .user(testUser)
                .status(OrderStatus.PENDING)
                .items(List.of())
                .build();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willReturn(order);

        // Act
        orderService.cancelOrder(1L, testUser);

        // Assert
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // CheckDB: verify save với trạng thái CANCELLED
        verify(orderRepository).save(argThat((Order o) -> o.getStatus() == OrderStatus.CANCELLED));
    }

    /**
     * TC-ORDER-09
     * Mục đích: cancelOrder — user không phải chủ đơn → RuntimeException "Không có quyền"
     * Input    : order.user.id = 2; caller user.id = 1
     * Expected : RuntimeException "Không có quyền"
     */
    @Test
    @DisplayName("TC-ORDER-09: cancelOrder — user không có quyền → RuntimeException")
    void cancelOrder_wrongUser_throwsException() {
        // Arrange
        User anotherUser = User.builder().id(2L).role(userRole).build();
        Order order = Order.builder()
                .id(1L)
                .user(anotherUser)
                .status(OrderStatus.PENDING)
                .build();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // Act & Assert
        assertThatThrownBy(() -> orderService.cancelOrder(1L, testUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("quyền");

        verify(orderRepository, never()).save(any());
    }

    /**
     * TC-ORDER-10
     * Mục đích: cancelOrder — order ở COMPLETED → RuntimeException "Không thể huỷ"
     * Input    : order.status = COMPLETED
     * Expected : RuntimeException "Không thể huỷ đơn"
     */
    @Test
    @DisplayName("TC-ORDER-10: cancelOrder COMPLETED → RuntimeException không thể huỷ")
    void cancelOrder_completedOrder_throwsException() {
        // Arrange
        Order order = Order.builder()
                .id(1L)
                .user(testUser)
                .status(OrderStatus.COMPLETED) // không thể huỷ
                .build();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // Act & Assert
        assertThatThrownBy(() -> orderService.cancelOrder(1L, testUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không thể huỷ");

        verify(orderRepository, never()).save(any());
    }

    // ─────────────────────────── approveRefund ──────────────────────────────

    /**
     * TC-ORDER-11
     * Mục đích: approveRefund — paymentService.callVnPayRefund trả false → RuntimeException "Hoàn tiền thất bại"
     * Input    : order ở CANCEL_REQUESTED, payment ở REFUND_PENDING; vnpay refund = false
     * Expected : RuntimeException "Hoàn tiền thất bại"; payment.status = REFUND_FAILED
     * CheckDB  : Yes — verify paymentRepository.findByOrder; verify paymentService.callVnPayRefund
     */
    @Test
    @DisplayName("TC-ORDER-11: approveRefund — VNPay refund thất bại → RuntimeException")
    void approveRefund_vnpayFailed_throwsException() {
        // Arrange
        OrderItem oi = new OrderItem();
        oi.setDrug(drug);
        oi.setQuantity(1);

        Order order = Order.builder()
                .id(1L)
                .user(testUser)
                .status(OrderStatus.CANCEL_REQUESTED)
                .paymentMethod(PaymentMethod.VNPAY)
                .items(List.of(oi))
                .build();

        Payment payment = Payment.builder()
                .id(100L)
                .order(order)
                .status(PaymentStatus.REFUND_PENDING)
                .build();

        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrder(order)).willReturn(Optional.of(payment));
        // VNPay trả về false (thất bại)
        given(paymentService.callVnPayRefund(payment, adminUser)).willReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> orderService.approveRefund(1L, adminUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Hoàn tiền thất bại");

        // CheckDB: verify payment.status được set REFUND_FAILED
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
        verify(paymentService).callVnPayRefund(payment, adminUser);
    }

    /**
     * TC-ORDER-12
     * Mục đích: approveRefund — order không ở CANCEL_REQUESTED → RuntimeException
     * Input    : order.status = PAID (chưa yêu cầu hoàn)
     * Expected : RuntimeException "Order không ở trạng thái yêu cầu hoàn"
     */
    @Test
    @DisplayName("TC-ORDER-12: approveRefund — order không ở CANCEL_REQUESTED → RuntimeException")
    void approveRefund_wrongStatus_throwsException() {
        // Arrange
        Order order = Order.builder()
                .id(1L)
                .user(testUser)
                .status(OrderStatus.PAID) // chưa yêu cầu hoàn
                .paymentMethod(PaymentMethod.VNPAY)
                .build();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // Act & Assert
        assertThatThrownBy(() -> orderService.approveRefund(1L, adminUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("yêu cầu hoàn");

        // CheckDB: paymentService.callVnPayRefund KHÔNG được gọi
        verify(paymentService, never()).callVnPayRefund(any(), any());
    }
}
