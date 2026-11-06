package com.lapzone.lapzoneweb;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import java.util.List;
import java.util.Optional;
import com.lapzone.lapzoneweb.model.repository.CartItemRepository;
import com.lapzone.lapzoneweb.model.service.CartService;
import com.lapzone.lapzoneweb.model.entity.CartItem;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import com.lapzone.lapzoneweb.model.entity.Product;

@ExtendWith(MockitoExtension.class)
public class CartServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;

    // TH1: Thêm sản phẩm chưa có trong giỏ → tạo CartItem mới
    @Test
    public void testAddToCart() {
        Product mockProduct = new Product();
        mockProduct.setId(1L);
        mockProduct.setPrice(100000.0);

        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(cartItemRepository.findByUserIdAndProduct(1L, mockProduct)).thenReturn(null);

        cartService.addToCart(1L, 1L, 1);

        verify(cartItemRepository, times(1)).save(any(CartItem.class));
    }

    // TH2: Xóa sản phẩm khỏi giỏ → gọi deleteById
    @Test
    public void testRemoveFromCart() {
        cartService.removeFromCart(7L, 1L);
        verify(cartItemRepository, times(1)).deleteByIdAndUserId(1L, 7L);
    }

    // TH3: Lấy danh sách giỏ hàng cùng sản phẩm bằng một fetch join
    @Test
    public void testGetCartItems() {
        cartService.getCartItems(1L);
        verify(cartItemRepository, times(1)).findByUserIdWithProduct(1L);
    }

    // TH4: Tính tổng giỏ hàng → gọi findByUserId để lấy danh sách
    @Test
    public void testGetCartTotal() {
        when(cartItemRepository.findByUserIdWithProduct(1L)).thenReturn(List.of());
        cartService.getCartTotal(1L);
        verify(cartItemRepository, times(1)).findByUserIdWithProduct(1L);
    }

    // TH5: Cập nhật số lượng hợp lệ → lưu lại CartItem
    @Test
    public void testUpdateQuantity() {
        CartItem mockCartItem = new CartItem();
        mockCartItem.setId(1L);
        mockCartItem.setQuantity(1);

        when(cartItemRepository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(mockCartItem));

        cartService.updateQuantity(7L, 1L, 2);

        verify(cartItemRepository, times(1)).save(mockCartItem);
    }

    @Test
    public void testAddToCartDoesNotExceedStock() {
        Product mockProduct = new Product();
        mockProduct.setId(1L);
        mockProduct.setStock(3);

        CartItem existingItem = new CartItem();
        existingItem.setId(1L);
        existingItem.setProduct(mockProduct);
        existingItem.setQuantity(2);

        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));
        when(cartItemRepository.findByUserIdAndProduct(1L, mockProduct)).thenReturn(existingItem);

        cartService.addToCart(1L, 1L, 5);

        assertEquals(3, existingItem.getQuantity());
        verify(cartItemRepository, times(1)).save(existingItem);
    }

    @Test
    public void testUpdateQuantityDoesNotExceedStock() {
        Product mockProduct = new Product();
        mockProduct.setId(1L);
        mockProduct.setStock(4);

        CartItem mockCartItem = new CartItem();
        mockCartItem.setId(1L);
        mockCartItem.setProduct(mockProduct);
        mockCartItem.setQuantity(1);

        when(cartItemRepository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(mockCartItem));

        cartService.updateQuantity(7L, 1L, 10);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository, times(1)).save(captor.capture());
        assertEquals(4, captor.getValue().getQuantity());
    }

    @Test
    public void testAnotherUsersItemCannotBeUpdated() {
        when(cartItemRepository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.empty());

        cartService.updateQuantity(7L, 1L, 10);

        verify(cartItemRepository, never()).save(any(CartItem.class));
        verify(cartItemRepository, never()).deleteByIdAndUserId(any(), any());
    }

    @Test
    public void testAnotherUsersItemCannotBeRemoved() {
        cartService.removeFromCart(7L, 1L);

        verify(cartItemRepository).deleteByIdAndUserId(1L, 7L);
    }

    @Test
    public void testCartTotalUsesPromotionalPrice() {
        Product product = new Product();
        product.setId(1L);
        product.setPrice(25_000_000.0);
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(2);
        when(cartItemRepository.findByUserIdWithProduct(7L)).thenReturn(List.of(item));

        assertEquals(45_000_000.0, cartService.getCartTotal(7L));
    }

}
