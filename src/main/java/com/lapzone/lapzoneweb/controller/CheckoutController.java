package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.entity.CartItem;
import com.lapzone.lapzoneweb.model.entity.Order;
import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.service.CartService;
import com.lapzone.lapzoneweb.model.service.InsufficientStockException;
import com.lapzone.lapzoneweb.model.service.OrderService;
import com.lapzone.lapzoneweb.model.service.ProductService;
import com.lapzone.lapzoneweb.model.service.PromotionService;
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

    @Autowired
    private com.lapzone.lapzoneweb.model.service.VoucherService voucherService;
    @Autowired
    private com.lapzone.lapzoneweb.model.service.VoucherCatalogService voucherCatalog;

    // ==========================================
    // 1. ĐÓN KHÁCH BẤM "MUA NGAY"
    // ==========================================
    @PostMapping("/buy-now")
    public String buyNow(@RequestParam("productId") Long productId,
                         @RequestParam("quantity") Integer quantity,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        Product product = productService.getProductById(productId);
        if (product == null || quantity == null || quantity <= 0) {
            redirectAttributes.addFlashAttribute("error", "Sản phẩm hoặc số lượng mua ngay không hợp lệ.");
            return "redirect:/";
        }
        
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
            if (buyNowItem == null || buyNowItem.getProduct() == null) {
                redirectAttributes.addFlashAttribute("error", "Không tìm thấy dữ liệu mua ngay.");
                return "redirect:/";
            }
            Product currentProduct = productService.getProductById(buyNowItem.getProduct().getId());
            Integer quantity = buyNowItem.getQuantity();
            if (currentProduct == null || currentProduct.getPrice() == null) {
                redirectAttributes.addFlashAttribute("error", "Sản phẩm mua ngay không còn khả dụng.");
                return "redirect:/";
            }
            if (quantity == null || quantity <= 0 || currentProduct.getStock() == null
                    || quantity > currentProduct.getStock()) {
                redirectAttributes.addFlashAttribute("error", "Số lượng mua vượt quá tồn kho hiện có.");
                return "redirect:/";
            }
            buyNowItem.setProduct(currentProduct);
            itemsToPay.add(buyNowItem);
            total = PromotionService.getDiscountedPrice(currentProduct) * quantity;
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
            itemsToPay = cartService.getSelectedCartItems(selectedItems, currentUser.getId());
            if (itemsToPay.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Không tìm thấy sản phẩm hợp lệ trong giỏ hàng của bạn.");
                return "redirect:/cart";
            }
            total = itemsToPay.stream()
                    .mapToDouble(item -> PromotionService.getDiscountedPrice(item.getProduct()) * item.getQuantity())
                    .sum();
            model.addAttribute("selectedItemIds", itemsToPay.stream().map(CartItem::getId).toList());
        }

        model.addAttribute("cartItems", itemsToPay);
        model.addAttribute("cartTotal", total);
        model.addAttribute("checkoutMode", "buynow".equals(mode) ? "buynow" : "cart");
        model.addAttribute("user", session.getAttribute("currentUser")); // Truyền null cũng không sao
        String checkoutKey = java.util.UUID.randomUUID().toString();
        session.setAttribute("VOUCHER_CHECKOUT_KEY", checkoutKey);
        model.addAttribute("checkoutKey", checkoutKey);
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        var details = authentication != null && authentication.getPrincipal() instanceof com.lapzone.lapzoneweb.security.CustomUserDetails customer
                ? customer : null;
        model.addAttribute("availableVouchers", voucherCatalog.availableFor(details == null ? null : details.getUser()));

        return "checkout";
    }

    // ==========================================
    // 3. XỬ LÝ CHỐT ĐƠN VÀ LƯU DATABASE
    // ==========================================
    public String processCheckout(String customerName, String customerPhone, String address, String paymentMethod,
            List<Long> selectedItemIds, String mode, HttpSession session, RedirectAttributes redirectAttributes) {
        return processCheckout(customerName, customerPhone, address, paymentMethod, selectedItemIds, mode, null, null, session, redirectAttributes);
    }

    @PostMapping("/checkout/process")
    public String processCheckout(
            @RequestParam("customerName") String customerName,
            @RequestParam("customerPhone") String customerPhone,
            @RequestParam("address") String address,
            @RequestParam("paymentMethod") String paymentMethod,
            @RequestParam(value = "selectedItemIds", required = false) List<Long> selectedItemIds,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "voucherCode", required = false) String voucherCode,
            @RequestParam(value = "checkoutKey", required = false) String checkoutKey,
            HttpSession session, RedirectAttributes redirectAttributes) {

        User currentUser = (User) session.getAttribute("currentUser");
        boolean buyNowMode = "buynow".equals(mode);
        if (!buyNowMode && currentUser == null) {
            return "redirect:/dangnhap";
        }
        if (!buyNowMode && (selectedItemIds == null || selectedItemIds.isEmpty())) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng chọn ít nhất 1 sản phẩm để thanh toán.");
            return "redirect:/cart";
        }
        try {
            boolean withVoucher = voucherCode != null && !voucherCode.isBlank();
            if (withVoucher) {
                currentUser = voucherUser();
                if (checkoutKey == null || !checkoutKey.equals(session.getAttribute("VOUCHER_CHECKOUT_KEY")))
                    throw new IllegalArgumentException("Phiên thanh toán không hợp lệ. Vui lòng tải lại trang thanh toán.");
            }
            Order newOrder;
            
            if (buyNowMode) {
                CartItem buyNowItem = (CartItem) session.getAttribute("BUY_NOW_ITEM");
                if (buyNowItem == null) {
                    throw new IllegalArgumentException("Không tìm thấy dữ liệu mua ngay.");
                }
                newOrder = withVoucher
                        ? orderService.placeOrderBuyNow(currentUser, customerName, customerPhone, address, buyNowItem, paymentMethod, voucherCode, checkoutKey)
                        : orderService.placeOrderBuyNow(currentUser, customerName, customerPhone, address, buyNowItem, paymentMethod);
                session.removeAttribute("BUY_NOW_ITEM");
            } else {
                newOrder = withVoucher
                        ? orderService.placeOrder(currentUser, customerName, customerPhone, address, selectedItemIds, paymentMethod, voucherCode, checkoutKey)
                        : orderService.placeOrder(currentUser, customerName, customerPhone, address, selectedItemIds, paymentMethod);
            }
            if (withVoucher) session.removeAttribute("VOUCHER_CHECKOUT_KEY");

            session.setAttribute("CHECKOUT_SUCCESS_ORDER_ID", newOrder.getId());
            // Gửi dữ liệu sang trang báo thành công để hiện QR
            redirectAttributes.addFlashAttribute("successMessage", "Mã đơn hàng: #" + newOrder.getId());
            redirectAttributes.addFlashAttribute("payMethod", paymentMethod);
            redirectAttributes.addFlashAttribute("totalAmount", newOrder.getTotalAmount());
            
            return "redirect:/checkout/success";
        } catch (InsufficientStockException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            if (selectedItemIds != null && !selectedItemIds.isEmpty()) {
                redirectAttributes.addAttribute("selectedItems", selectedItemIds);
                return "redirect:/checkout";
            }
            return "redirect:/checkout?mode=buynow";
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("error", "Yêu cầu đã được xử lý hoặc mã giảm giá không còn khả dụng. Vui lòng kiểm tra đơn hàng trước khi thử lại.");
            return "redirect:/store";
        } catch (Exception e) {
            String message = voucherCode != null && !voucherCode.isBlank() && !(e instanceof IllegalArgumentException)
                    ? "Không thể hoàn tất đơn hàng. Vui lòng kiểm tra đơn hàng trước khi thử lại."
                    : "Có lỗi xảy ra: " + e.getMessage();
            redirectAttributes.addFlashAttribute("error", message);
            return buyNowMode ? "redirect:/checkout?mode=buynow" : "redirect:/cart";
        }
    }

    @PostMapping("/checkout/voucher-preview")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> previewVoucher(
            @RequestParam String voucherCode, @RequestParam(required = false) String mode,
            @RequestParam(required = false) List<Long> selectedItemIds, HttpSession session) {
        try {
            User user = voucherUser();
            List<CartItem> items;
            if ("buynow".equals(mode)) {
                CartItem item = (CartItem) session.getAttribute("BUY_NOW_ITEM");
                if (item == null) throw new IllegalArgumentException("Không tìm thấy sản phẩm mua ngay.");
                items = List.of(item);
            } else {
                if (selectedItemIds == null || selectedItemIds.isEmpty())
                    throw new IllegalArgumentException("Vui lòng chọn sản phẩm trong giỏ hàng.");
                items = cartService.getSelectedCartItems(selectedItemIds, user.getId());
            }
            var quote = voucherService.preview(voucherCode, user, items);
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of(
                    "subtotal", quote.subtotal(), "discount", quote.discount(), "total", quote.total()));
        } catch (IllegalArgumentException e) {
            return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    private User voucherUser() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof com.lapzone.lapzoneweb.security.CustomUserDetails details)
            return details.getUser();
        throw new IllegalArgumentException("Vui lòng đăng nhập để sử dụng mã giảm giá.");
    }

    // ==========================================
    // 4. TRANG BÁO THÀNH CÔNG
    // ==========================================
    @GetMapping("/checkout/success")
    public String showSuccessPage(HttpSession session, Model model) {
        Long orderId = (Long) session.getAttribute("CHECKOUT_SUCCESS_ORDER_ID");
        if (orderId == null) {
            return "redirect:/";
        }
        model.addAttribute("successOrderId", orderId);
        return "checkout_success"; 
    }
}
