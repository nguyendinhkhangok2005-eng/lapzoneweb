package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.entity.Order;
import com.lapzone.lapzoneweb.model.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OrderController {

    @Autowired
    private OrderService orderService;

    // 1. Hiển thị trang nhập thông tin tra cứu
    @GetMapping("/tracuudonhang")
    public String showLookupPage() {
        return "order_lookup"; 
    }

    // 2. Xử lý khi khách hàng bấm "Tra cứu"
    @PostMapping("/tracuudonhang")
    public String processLookup(@RequestParam("orderId") Long orderId,
                                @RequestParam("customerPhone") String customerPhone,
                                RedirectAttributes redirectAttributes) {
        try {
            // Tìm đơn hàng theo ID
            Order order = orderService.getOrderById(orderId);
            
            // Bảo mật: Kiểm tra xem Số điện thoại khách nhập có KHỚP với SĐT lúc đặt hàng không
            if (order.getCustomerPhone().equals(customerPhone.trim())) {
                // Nếu đúng, chuyển thẳng đến trang xem chi tiết Hóa đơn
                return "redirect:/order/detail/" + orderId;
            } else {
                redirectAttributes.addFlashAttribute("error", "Số điện thoại không khớp với đơn hàng này!");
                return "redirect:/tracuudonhang";
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy mã đơn hàng #" + orderId);
            return "redirect:/tracuudonhang";
        }
    }

    // 3. Hiển thị tờ Hóa đơn (Chức năng bạn đã hỏi ở turn trước)
    @GetMapping("/order/detail/{id}")
    public String viewOrderDetail(@PathVariable("id") Long id, Model model) {
        try {
            Order order = orderService.getOrderById(id);
            model.addAttribute("order", order);
            return "order_detail"; // Trỏ tới file order_detail.html mà anh em mình thiết kế lúc nãy
        } catch (Exception e) {
            return "redirect:/";
        }
    }
}