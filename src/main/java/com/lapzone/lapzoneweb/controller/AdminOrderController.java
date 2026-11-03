package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.entity.Order;
import com.lapzone.lapzoneweb.model.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/orders")
public class AdminOrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping
    public String listOrders(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.util.Date startDate,
            @RequestParam(value = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.util.Date endDate,
            @RequestParam(value = "page", defaultValue = "0") int page,   // [MỚI] trang hiện tại
            @RequestParam(value = "size", defaultValue = "20") int size,  // [MỚI] số bản ghi/trang
            Model model) {
        
        if (endDate != null) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(endDate);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
            cal.set(java.util.Calendar.MINUTE, 59);
            cal.set(java.util.Calendar.SECOND, 59);
            endDate = cal.getTime();
        }

        // [FIX] Dùng phân trang: chỉ load 20 đơn hàng thay vì toàn bộ 500.000
        Page<Order> orderPage = orderService.searchOrdersPaged(keyword, status, startDate, endDate, page, size);
        
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        if (startDate != null) model.addAttribute("startDate", sdf.format(startDate));
        if (endDate != null) model.addAttribute("endDate", sdf.format(endDate));

        model.addAttribute("orders", orderPage.getContent());      // Chỉ 20 bản ghi
        model.addAttribute("currentPage", page);                   // Trang hiện tại
        model.addAttribute("totalPages", orderPage.getTotalPages()); // Tổng số trang
        model.addAttribute("totalElements", orderPage.getTotalElements()); // Tổng số đơn hàng
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        return "admin/orders"; 
    }

    @PostMapping("/update-status")
    public String updateStatus(@RequestParam("orderId") Long orderId, 
                               @RequestParam("newStatus") String newStatus,
                               RedirectAttributes ra) {
        try {
            orderService.updateOrderStatus(orderId, newStatus);
            ra.addFlashAttribute("success", "Cập nhật đơn hàng #" + orderId + " thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/orders";
    }
}