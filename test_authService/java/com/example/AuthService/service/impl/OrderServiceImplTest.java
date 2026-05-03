package com.example.AuthService.service.impl;

import com.example.AuthService.dto.request.CreateOrderRequest;
import com.example.AuthService.dto.request.OrderItemRequest;
import com.example.AuthService.entity.Drug;
import com.example.AuthService.entity.Order;
import com.example.AuthService.entity.OrderItem;
import com.example.AuthService.entity.User;
import com.example.AuthService.enums.OrderStatus;
import com.example.AuthService.enums.PaymentMethod;
import com.example.AuthService.repository.DrugRepository;
import com.example.AuthService.repository.OrderRepository;
import com.example.AuthService.repository.PaymentRepository;
import com.example.AuthService.service.InventoryService;
import com.example.AuthService.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test {@link OrderServiceImpl} (một phần luồng chính). CheckDB: captor {@link OrderRepository#save(Order)}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl")
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private DrugRepository drugRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentService paymentService;
    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User customer(long id) {
        return User.builder().id(id).email("c@c.com").name("C").password("p")
                .enabled(true).accountNonExpired(true).accountNonLocked(true).credentialsNonExpired(true).build();
    }

    /** Test Case ID: TC-ORDER-CREATE-01 */
    @Test
    @DisplayName("createOrder builds pending order with line items")
    void createOrderBuildsPendingOrder() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setShippingAddress("addr");
        request.setReceiverName("R");
        request.setReceiverPhone("090");
        OrderItemRequest line = new OrderItemRequest();
        line.setDrugId(1L);
        line.setQuantity(2);
        request.setItems(List.of(line));

        Drug drug = Drug.builder().id(1L).name("Med").price(BigDecimal.valueOf(15)).importPrice(BigDecimal.ONE).build();
        when(drugRepository.findById(1L)).thenReturn(Optional.of(drug));
        when(inventoryService.calculateStock(1L)).thenReturn(100);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = customer(10L);
        Order saved = orderService.createOrder(request, user);

        assertEquals(OrderStatus.PENDING, saved.getStatus());
        assertEquals(1, saved.getItems().size());
        assertEquals(user, saved.getUser());
        verify(orderRepository).save(any(Order.class));
    }

    /** Test Case ID: TC-ORDER-CANCEL-01 */
    @Test
    @DisplayName("cancelOrder sets CANCELLED when PENDING")
    void cancelOrderSetsCancelledWhenPending() {
        Order order = Order.builder().id(3L).user(customer(10L)).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));

        orderService.cancelOrder(3L, customer(10L));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertEquals(OrderStatus.CANCELLED, captor.getValue().getStatus());
    }

    /** Test Case ID: TC-ORDER-COD-01 */
    @Test
    @DisplayName("confirmCodPayment sets COD method when pending")
    void confirmCodPaymentSetsCod() {
        Order order = Order.builder().id(4L).user(customer(10L)).status(OrderStatus.PENDING)
                .items(List.of(OrderItem.builder().quantity(1).build()))
                .build();
        when(orderRepository.findById(4L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order out = orderService.confirmCodPayment(4L, customer(10L));

        assertEquals(PaymentMethod.COD, out.getPaymentMethod());
    }
}
