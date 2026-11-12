package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.model.entity.CartItem;
import com.lapzone.lapzoneweb.model.entity.Order;
import com.lapzone.lapzoneweb.model.entity.OrderDetail;
import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.CartItemRepository;
import com.lapzone.lapzoneweb.model.repository.OrderDetailRepository;
import com.lapzone.lapzoneweb.model.repository.OrderRepository;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import com.lapzone.lapzoneweb.model.service.InsufficientStockException;
import com.lapzone.lapzoneweb.model.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderDetailRepository orderDetailRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private com.lapzone.lapzoneweb.model.service.OrderAuthorization orderAuthorization;
    @Mock
    private com.lapzone.lapzoneweb.model.repository.OrderStatusChangeRepository statusChanges;

    @InjectMocks
    private OrderService orderService;

    private User owner() { User user = new User(); user.setId(7L); return user; }

    @Test
    void placeOrderDecrementsStockByRequestedQuantity() {
        Product product = product(1L, "Laptop", 5);
        CartItem item = cartItem(10L, product, 2);
        when(cartItemRepository.findByIdInAndUserIdWithProduct(List.of(10L), 7L)).thenReturn(List.of(item));
        when(productRepository.decrementStockIfAvailable(1L, 2)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = orderService.placeOrder(owner(), "Khách hàng", "0123", "Địa chỉ",
                List.of(10L), "COD");

        assertEquals("CHỜ XÁC NHẬN", order.getStatus());
        verify(productRepository).decrementStockIfAvailable(1L, 2);
        verify(orderDetailRepository).save(any(OrderDetail.class));
        verify(cartItemRepository).deleteByIdAndUserId(10L, 7L);
    }

    @Test
    void placeOrderRejectsInsufficientStockWithoutSavingOrder() {
        Product product = product(1L, "Laptop", 1);
        CartItem item = cartItem(10L, product, 2);
        when(cartItemRepository.findByIdInAndUserIdWithProduct(List.of(10L), 7L)).thenReturn(List.of(item));
        when(productRepository.decrementStockIfAvailable(1L, 2)).thenReturn(0);

        InsufficientStockException exception = assertThrows(InsufficientStockException.class,
                () -> orderService.placeOrder(owner(), "Khách hàng", "0123", "Địa chỉ",
                        List.of(10L), "COD"));

        assertTrue(exception.getMessage().contains("Laptop"));
        assertEquals(1, product.getStock());
        verify(orderRepository, never()).save(any(Order.class));
        verify(orderDetailRepository, never()).save(any(OrderDetail.class));
        verify(cartItemRepository, never()).delete(item);
    }

    @Test
    void placeOrderWithMultipleProductsDoesNotCreateOrderWhenAnyProductIsUnavailable() {
        Product available = product(1L, "Laptop A", 5);
        Product unavailable = product(2L, "Laptop B", 0);
        CartItem firstItem = cartItem(10L, available, 2);
        CartItem secondItem = cartItem(11L, unavailable, 1);
        when(cartItemRepository.findByIdInAndUserIdWithProduct(List.of(10L, 11L), 7L)).thenReturn(List.of(firstItem, secondItem));
        when(productRepository.decrementStockIfAvailable(1L, 2)).thenReturn(1);
        when(productRepository.decrementStockIfAvailable(2L, 1)).thenReturn(0);

        InsufficientStockException exception = assertThrows(InsufficientStockException.class,
                () -> orderService.placeOrder(owner(), "Khách hàng", "0123", "Địa chỉ",
                        List.of(10L, 11L), "COD"));

        assertTrue(exception.getMessage().contains("Laptop B"));
        assertEquals(5, available.getStock());
        assertEquals(0, unavailable.getStock());
        verify(orderRepository, never()).save(any(Order.class));
        verify(orderDetailRepository, never()).save(any(OrderDetail.class));
    }

    @Test
    void buyNowForGuestDecrementsStock() {
        Product product = product(3L, "Laptop mua ngay", 4);
        CartItem buyNowItem = cartItem(null, product, 1);
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productRepository.decrementStockIfAvailable(3L, 1)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.placeOrderBuyNow(null, "Khách vãng lai", "0456", "Địa chỉ",
                buyNowItem, "COD");

        verify(productRepository).decrementStockIfAvailable(3L, 1);
        verify(cartItemRepository, never()).delete(any(CartItem.class));
    }

    @Test
    void savesPromotionalPriceInOrderTotalAndOrderDetail() {
        Product product = product(1L, "Laptop giảm giá", 2);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.decrementStockIfAvailable(1L, 1)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CartItem buyNowItem = cartItem(null, product, 1);

        Order order = orderService.placeOrderBuyNow(null, "Khách hàng", "0123",
                "Địa chỉ", buyNowItem, "COD");

        assertEquals(90.0, order.getTotalAmount());
        ArgumentCaptor<OrderDetail> detailCaptor = ArgumentCaptor.forClass(OrderDetail.class);
        verify(orderDetailRepository).save(detailCaptor.capture());
        assertEquals(90.0, detailCaptor.getValue().getPrice());
    }

    @Test
    void cancellingOrderRestoresStockOnlyOnce() {
        Order order = new Order();
        order.setId(50L);
        order.setStatus("CHỜ XÁC NHẬN");
        Product product = product(5L, "Laptop hoàn kho", 0);
        OrderDetail detail = new OrderDetail();
        detail.setProduct(product);
        detail.setQuantity(2);
        when(orderRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(order));
        when(orderDetailRepository.findByOrderId(50L)).thenReturn(List.of(detail));
        when(productRepository.incrementStock(5L, 2)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.updateOrderStatus(50L, "ĐÃ HỦY");
        orderService.updateOrderStatus(50L, "ĐÃ HỦY");

        assertEquals("ĐÃ HỦY", order.getStatus());
        verify(productRepository).incrementStock(5L, 2);
    }

    private Product product(Long id, String name, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setStock(stock);
        product.setPrice(100.0);
        return product;
    }

    private CartItem cartItem(Long id, Product product, int quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }
}
