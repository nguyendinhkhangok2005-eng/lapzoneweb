package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.repository.CategoryRepository;
import com.lapzone.lapzoneweb.model.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryRepository categoryRepository;


    @GetMapping("/product_detail")
    public String showProductDetail(@RequestParam("id") Long id, Model model) {
        
  
        Product product = productService.getProductById(id);
        
        if (product != null) {
            List<Product> relatedProducts = productService.getRelatedProducts(product.getCategory().getId(), product.getId());
            model.addAttribute("relatedProducts", relatedProducts);
            model.addAttribute("product", product);
            return "product_detail"; 
        } else {
            return "redirect:/"; 
        }
    }

   
    @GetMapping("/search")
    public String searchPage(
            @RequestParam(value = "query", required = false, defaultValue = "") String query,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "minPrice", required = false) Double minPrice,
            @RequestParam(value = "maxPrice", required = false) Double maxPrice,
            @RequestParam(value = "cpu", required = false) String cpu,     
            @RequestParam(value = "gpu", required = false) String gpu,
            @RequestParam(value = "ram", required = false) String ram,
            @RequestParam(value = "storage", required = false) String storage,
            @RequestParam(value = "screen", required = false) String screen,
            @RequestParam(value = "sort", defaultValue = "newest") String sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {
        
        int pageSize = 9;
        
        org.springframework.data.domain.Sort sortObj;
        if ("price_asc".equals(sort)) {
            sortObj = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "price");
        } else if ("price_desc".equals(sort)) {
            sortObj = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "price");
        } else {
            sortObj = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id");
        }
        
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, pageSize, sortObj);
        org.springframework.data.domain.Page<Product> productPage = productService.searchProducts(
                query, categoryId, minPrice, maxPrice, cpu, gpu, ram, storage, screen, pageable);
        
        model.addAttribute("productPage", productPage);
        model.addAttribute("products", productPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", productPage.getTotalPages());
        
        model.addAttribute("keyword", query);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("selectedCategoryId", categoryId);
        
        // Bổ sung logic lấy tên danh mục để hiển thị lên UI
        if (categoryId != null) {
            model.addAttribute("selectedCategoryName", categoryRepository.findById(categoryId)
                .map(cat -> cat.getName())
                .orElse(null));
        } else {
            model.addAttribute("selectedCategoryName", null);
        }
        model.addAttribute("selectedMinPrice", minPrice);
        model.addAttribute("selectedMaxPrice", maxPrice);
        model.addAttribute("selectedCpu", cpu); 
        model.addAttribute("selectedGpu", gpu); 
        model.addAttribute("selectedRam", ram); 
        model.addAttribute("selectedStorage", storage); 
        model.addAttribute("selectedScreen", screen); 
        model.addAttribute("selectedSort", sort); 
        return "search_results"; 
    }
}
