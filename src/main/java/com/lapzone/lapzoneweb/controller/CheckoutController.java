package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.entity.CartItem;
import com.lapzone.lapzoneweb.model.entity.Order;
import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.service.CartService;
import com.lapzone.lapzoneweb.model.service.OrderService;
import com.lapzone.lapzoneweb.model.service.ProductService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
public class CheckoutController {

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    // ==========================================
    // 1. ĐÓN KHÁCH BẤM "MUA NGAY"
    // ==========================================
    @PostMapping("/buy-now")
    public String buyNow(@RequestParam("productId") Long productId,
                         @RequestParam("quantity") Integer quantity,
                         HttpSession session) {
        Product product = productService.getProductById(productId);
        
        CartItem tempItem = new CartItem();
        tempItem.setProduct(product);
        tempItem.setQuantity(quantity);
        
        session.setAttribute("BUY_NOW_ITEM", tempItem);
        return "redirect:/checkout?mode=buynow";
    }

    // ==========================================
    // 2. HIỂN THỊ TRANG THANH TOÁN
    // ==========================================
    @GetMapping("/checkout")
    public String showCheckoutPage(
            @RequestParam(value = "selectedItems", required = false) List<Long> selectedItems,
            @RequestParam(value = "mode", required = false) String mode,
            HttpSession session, Model model, RedirectAttributes redirectAttributes) {

        List<CartItem> itemsToPay = new ArrayList<>();
        Double total = 0.0;

        // Nếu là luồng Mua Ngay (lấy từ Session)
        if ("buynow".equals(mode)) {
            CartItem buyNowItem = (CartItem) session.getAttribute("BUY_NOW_ITEM");
            if (buyNowItem == null) return "redirect:/"; // Tránh lỗi null pointer
            
            itemsToPay.add(buyNowItem);
            total = buyNowItem.getProduct().getPrice() * buyNowItem.getQuantity();
        } 
        // Nếu là luồng Giỏ hàng (lấy từ DB)
        else {
            User currentUser = (User) session.getAttribute("currentUser");
            if (currentUser == null) return "redirect:/dangnhap"; // Chỉ luồng này mới đòi đăng nhập

            if (selectedItems == null || selectedItems.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Vui lòng chọn ít nhất 1 sản phẩm để thanh toán!");
                return "redirect:/cart";
            }
            // [FIX] Query DB 1 lần duy nhất rồi tính tổng trên Java, tránh gọi DB 2 lần
            itemsToPay = cartService.getSelectedCartItems(selectedItems);
            total = itemsToPay.stream()
                    .mapToDouble(item -> item.getProduct().getPrice() * item.getQuantity())
                    .sum();
            model.addAttribute("selectedItemIds", selectedItems);
        }

        model.addAttribute("cartItems", itemsToPay);
        model.addAttribute("cartTotal", total);
        model.addAttribute("user", session.getAttribute("currentUser")); // Truyền null cũng không sao

        return "checkout";
    }

    // ==========================================
    // 3. XỬ LÝ CHỐT ĐƠN VÀ LƯU DATABASE
    // ==========================================
    @PostMapping("/checkout/process")
    public String processCheckout(
            @RequestParam("customerName") String customerName,
            @RequestParam("customerPhone") String customerPhone,
            @RequestParam("address") String address,
            @RequestParam("paymentMethod") String paymentMethod,
            @RequestParam(value = "selectedItemIds", required = false) List<Long> selectedItemIds,
            HttpSession session, RedirectAttributes redirectAttributes) {

        User currentUser = (User) session.getAttribute("currentUser");
        try {
            Order newOrder;
            
            // Xử lý giỏ hàng
            if (selectedItemIds != null && !selectedItemIds.isEmpty()) {
                newOrder = orderService.placeOrder(currentUser, customerName, customerPhone, address, selectedItemIds, paymentMethod);
            } 
            // Xử lý Mua Ngay
            else {
                CartItem buyNowItem = (CartItem) session.getAttribute("BUY_NOW_ITEM");
                if (buyNowItem == null) throw new RuntimeException("Không tìm thấy dữ liệu mua ngay!");
                
                newOrder = orderService.placeOrderBuyNow(currentUser, customerName, customerPhone, address, buyNowItem, paymentMethod);
                session.removeAttribute("BUY_NOW_ITEM"); // Chốt xong thì dọn Session
            }

            // Gửi dữ liệu sang trang báo thành công để hiện QR
            redirectAttributes.addFlashAttribute("successMessage", "Mã đơn hàng: #" + newOrder.getId());
            redirectAttributes.addFlashAttribute("payMethod", paymentMethod);
            redirectAttributes.addFlashAttribute("totalAmount", newOrder.getTotalAmount());
            
            return "redirect:/checkout/success";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra: " + e.getMessage());
            return "redirect:/";
        }
    }

    // ==========================================
    // 4. TRANG BÁO THÀNH CÔNG
    // ==========================================
    @GetMapping("/checkout/success")
    public String showSuccessPage(HttpSession session) {
        // Xóa lệnh kiểm tra đăng nhập đi để khách Mua ngay vẫn xem được mã QR
        return "checkout_success"; 
    }
}