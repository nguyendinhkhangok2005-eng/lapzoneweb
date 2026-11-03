package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.ProductService;
import com.lapzone.lapzoneweb.model.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    @Autowired
    private CategoryRepository categoryRepository;
    
    @Autowired
    private ProductService productService;

    @GetMapping("/")
    public String showHomePage(@RequestParam(value = "categoryId", required = false) Long categoryId, Model model) {
        
        // 1. LUÔN LUÔN TRUYỀN DANH MỤC: Để giao diện luôn vẽ được các nút bấm tên hãng
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("selectedCatId", categoryId); // Trả về ID đang chọn để HTML bôi đỏ nút

        // 2. XỬ LÝ LOGIC HIỂN THỊ
        if (categoryId != null) {
            // NẾU CÓ CHỌN HÃNG: Lấy danh sách máy của hãng đó truyền vào biến "products"
            model.addAttribute("products", productService.getProductsByCategoryId(categoryId));
        } else {
            // NẾU KHÔNG CHỌN HÃNG (Trang chủ mặc định): Giữ nguyên 100% code cũ của bạn
            model.addAttribute("referenceProducts", productService.getNewProducts());
            model.addAttribute("laptopProducts", productService.getProductsByBrand("Lenovo")); 
            model.addAttribute("appleProducts", productService.getProductsByBrand("Apple"));
            model.addAttribute("newProducts", productService.getNewProducts());
        }

        return "index";
    }
}