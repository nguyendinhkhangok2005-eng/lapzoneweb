package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.entity.ProductDetail;
import com.lapzone.lapzoneweb.model.service.AdminService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.security.access.prepost.PreAuthorize;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @GetMapping("/dashboard")
    public String showDashboard(
            @RequestParam(value = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.util.Date startDate,
            @RequestParam(value = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.util.Date endDate,
            Model model) {

        // Nếu không truyền startDate/endDate, mặc định lấy 7 ngày gần nhất
        if (startDate == null || endDate == null) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
            cal.set(java.util.Calendar.MINUTE, 59);
            cal.set(java.util.Calendar.SECOND, 59);
            endDate = cal.getTime();

            cal.add(java.util.Calendar.DAY_OF_MONTH, -7);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            startDate = cal.getTime();
        } else {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(endDate);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
            cal.set(java.util.Calendar.MINUTE, 59);
            cal.set(java.util.Calendar.SECOND, 59);
            endDate = cal.getTime();
        }

        java.util.List<com.lapzone.lapzoneweb.model.entity.Order> orders = adminService.getCompletedOrdersBetweenDates(startDate, endDate);
        Double totalRevenue = adminService.calculateRevenueBetweenDates(startDate, endDate);

        model.addAttribute("countProducts", adminService.countProducts());
        model.addAttribute("countUsers", adminService.countUsers());
        
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        model.addAttribute("startDate", sdf.format(startDate));
        model.addAttribute("endDate", sdf.format(endDate));
        model.addAttribute("countOrders", orders.size()); 
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("orders", orders);

        return "admin/dashboard";
    }

    @GetMapping("/dashboard/export")
    public void exportReportCSV(
            @RequestParam(value = "startDate", required = true) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.util.Date startDate,
            @RequestParam(value = "endDate", required = true) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.util.Date endDate,
            jakarta.servlet.http.HttpServletResponse response) {

        try {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(endDate);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
            cal.set(java.util.Calendar.MINUTE, 59);
            cal.set(java.util.Calendar.SECOND, 59);
            endDate = cal.getTime();

            java.util.List<com.lapzone.lapzoneweb.model.entity.Order> orders = adminService.getCompletedOrdersBetweenDates(startDate, endDate);
            Double totalRevenue = adminService.calculateRevenueBetweenDates(startDate, endDate);

            response.setContentType("text/csv; charset=UTF-8");
            
            String fileName = "BaoCao_DoanhThu_Lapzone";
            java.text.SimpleDateFormat fileSdf = new java.text.SimpleDateFormat("ddMMyyyy");
            if (startDate != null && endDate != null) {
                fileName += "_Tu_" + fileSdf.format(startDate) + "_Den_" + fileSdf.format(endDate);
            } else {
                fileName += "_" + fileSdf.format(new java.util.Date());
            }
            fileName += ".csv";
            
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

            java.io.PrintWriter writer = response.getWriter();
            writer.write('\ufeff'); // BOM

            writer.println("Mã đơn hàng,Khách hàng,Số điện thoại,Ngày tạo,Trạng thái,Tổng tiền (VNĐ)");
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm");

            for (com.lapzone.lapzoneweb.model.entity.Order o : orders) {
                String line = String.format("%d,\"%s\",\"%s\",\"%s\",\"%s\",%.0f",
                        o.getId(),
                        o.getCustomerName().replace("\"", "\"\""),
                        o.getCustomerPhone(),
                        sdf.format(o.getOrderDate()),
                        o.getStatus(),
                        o.getTotalAmount()
                );
                writer.println(line);
            }

            writer.println(",,,,Tổng Doanh Thu:," + String.format("%.0f", totalRevenue));
            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
   @GetMapping("/products")
    public String listProducts(Model model) {

        model.addAttribute("products", adminService.getAllProducts());
        model.addAttribute("categories", adminService.getAllCategories());
        
        Product product = new Product();
        product.setProductDetail(new ProductDetail()); 
        model.addAttribute("product", product);
        
        return "admin/products";
    }


    @PostMapping("/products/save")
    public String saveProduct(@ModelAttribute("product") Product product,
                              @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                              @RequestParam(value = "subImages", required = false) MultipartFile[] subImages,
                              org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        
        try {
            if (product.getProductDetail() != null) {
                product.getProductDetail().setProduct(product);
            }
            if (product.getStock() == null) {
                product.setStock(1); 
            }

            adminService.saveProductWithImages(product, imageFile, subImages);
            redirectAttributes.addFlashAttribute("successMessage", "Thêm/Cập nhật sản phẩm thành công!");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi lưu sản phẩm: " + e.getMessage());
        }

        return "redirect:/admin/products";
    }

  
    @GetMapping("/products/edit/{id}")
    public String editProduct(@PathVariable("id") Long id, Model model) {
        Product existingProduct = adminService.getProductById(id);
        

        if (existingProduct.getProductDetail() == null) {
            existingProduct.setProductDetail(new ProductDetail());
        }

        model.addAttribute("product", existingProduct);
        model.addAttribute("products", adminService.getAllProducts()); 
        model.addAttribute("categories", adminService.getAllCategories());
        
        return "admin/products";
    }


    @GetMapping("/products/delete/{id}")
    public String deleteProduct(@PathVariable("id") Long id, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            adminService.deleteProduct(id);
            redirectAttributes.addFlashAttribute("successMessage", "Xóa sản phẩm thành công!");
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa sản phẩm vì đang có trong đơn hàng hoặc giỏ hàng.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi xóa sản phẩm: " + e.getMessage());
        }
        return "redirect:/admin/products";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
     
        session.removeAttribute("currentUser");
        session.invalidate(); 
        return "redirect:/login"; 
    }
}