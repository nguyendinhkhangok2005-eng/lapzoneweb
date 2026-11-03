package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private UserService userService;

    // 1. Hiển thị danh sách khách hàng / người dùng (có phân trang + tìm kiếm)
    @GetMapping
    public String listUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Model model) {

        // [FIX] Dùng phân trang: chỉ load 20 users thay vì toàn bộ 100.000
        Page<User> userPage = userService.searchUsersPaged(keyword, page, size);

        model.addAttribute("users", userPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", userPage.getTotalPages());
        model.addAttribute("totalElements", userPage.getTotalElements());
        model.addAttribute("keyword", keyword);
        return "admin/users";
    }

    // 2. Thay đổi quyền (Role)
    @PostMapping("/update-role")
    public String updateRole(@RequestParam("userId") Long userId, 
                             @RequestParam("role") String role, 
                             RedirectAttributes ra) {
        try {
            User user = userService.getUserById(userId);
            user.setRole(role);
            userService.saveUser(user);
            ra.addFlashAttribute("success", "Cập nhật quyền cho người dùng thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // 3. Xóa tài khoản
    @PostMapping("/delete")
    public String deleteUser(@RequestParam("userId") Long userId, RedirectAttributes ra) {
        try {
            userService.deleteUser(userId);
            ra.addFlashAttribute("success", "Đã xóa tài khoản thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Không thể xóa do người này đã có lịch sử mua hàng!");
        }
        return "redirect:/admin/users";
    }
}