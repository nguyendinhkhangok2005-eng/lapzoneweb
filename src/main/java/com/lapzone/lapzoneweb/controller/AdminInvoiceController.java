package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.entity.Order;
import com.lapzone.lapzoneweb.model.service.OrderService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.text.SimpleDateFormat;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;

@Controller
@RequestMapping("/admin/invoices")
public class AdminInvoiceController {

    @Autowired
    private OrderService orderService;

    // 1. Hiển thị giao diện hóa đơn và thống kê
    @GetMapping
    public String listInvoices(
            @RequestParam(value = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.util.Date startDate,
            @RequestParam(value = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.util.Date endDate,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Model model) {
            
        if (endDate != null) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(endDate);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
            cal.set(java.util.Calendar.MINUTE, 59);
            cal.set(java.util.Calendar.SECOND, 59);
            endDate = cal.getTime();
        }

        // Dùng phân trang: chỉ load 20 bản ghi/trang thay vì toàn bộ
        org.springframework.data.domain.Page<Order> invoicePage =
                orderService.getValidInvoicesPaged(startDate, endDate, page, size);

        model.addAttribute("invoices", invoicePage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", invoicePage.getTotalPages());
        model.addAttribute("totalElements", invoicePage.getTotalElements());
        
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        if (startDate != null) model.addAttribute("startDate", sdf.format(startDate));
        if (endDate != null) model.addAttribute("endDate", sdf.format(endDate));

        model.addAttribute("monthlyRevenue", orderService.getCurrentMonthRevenue());
        return "admin/invoices";
    }

    // 1.5. IN BÁO CÁO DOANH THU (HTML PRINT)
    @GetMapping("/print")
    public String printReport(
            @RequestParam(value = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.util.Date startDate,
            @RequestParam(value = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.util.Date endDate,
            Model model) {
            
        if (endDate != null) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(endDate);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
            cal.set(java.util.Calendar.MINUTE, 59);
            cal.set(java.util.Calendar.SECOND, 59);
            endDate = cal.getTime();
        }

        List<Order> invoices = orderService.getValidInvoices(startDate, endDate);
        model.addAttribute("invoices", invoices);
        
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy");
        model.addAttribute("startDateStr", startDate != null ? sdf.format(startDate) : "Đầu kỳ");
        model.addAttribute("endDateStr", endDate != null ? sdf.format(endDate) : sdf.format(new java.util.Date()));

        // Tính tổng doanh thu
        double totalRevenue = invoices.stream().mapToDouble(Order::getTotalAmount).sum();
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalOrders", invoices.size());

        // Thống kê theo hãng (giả lập duyệt qua order details)
        java.util.Map<String, Double> brandRevenue = new java.util.HashMap<>();
        java.util.Map<String, Integer> brandQuantity = new java.util.HashMap<>();
        
        for (Order order : invoices) {
            if (order.getOrderDetails() != null) {
                for (com.lapzone.lapzoneweb.model.entity.OrderDetail detail : order.getOrderDetails()) {
                    String brandName = (detail.getProduct() != null && detail.getProduct().getCategory() != null) 
                                       ? detail.getProduct().getCategory().getName() 
                                       : "Khác";
                    
                    brandRevenue.put(brandName, brandRevenue.getOrDefault(brandName, 0.0) + (detail.getPrice() * detail.getQuantity()));
                    brandQuantity.put(brandName, brandQuantity.getOrDefault(brandName, 0) + detail.getQuantity());
                }
            }
        }
        
        model.addAttribute("brandRevenue", brandRevenue);
        model.addAttribute("brandQuantity", brandQuantity);

        return "admin/print_report"; 
    }

    // 2. CHỨC NĂNG XUẤT EXCEL
    @GetMapping("/export-excel")
    public void exportToExcel(
            @RequestParam(value = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.util.Date startDate,
            @RequestParam(value = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.util.Date endDate,
            HttpServletResponse response) throws IOException {
            
        if (endDate != null) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(endDate);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
            cal.set(java.util.Calendar.MINUTE, 59);
            cal.set(java.util.Calendar.SECOND, 59);
            endDate = cal.getTime();
        }

        // Cấu hình file trả về là Excel (Dùng chuẩn Jakarta)
        response.setContentType("application/octet-stream");
        
        String fileName = "ChungTu_Lapzone";
        java.text.SimpleDateFormat fileSdf = new java.text.SimpleDateFormat("ddMMyyyy");
        if (startDate != null && endDate != null) {
            fileName += "_Tu_" + fileSdf.format(startDate) + "_Den_" + fileSdf.format(endDate);
        } else {
            fileName += "_" + fileSdf.format(new java.util.Date());
        }
        fileName += ".xlsx";
        
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        List<Order> invoices = orderService.getValidInvoices(startDate, endDate);

        // Tạo file Excel
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Danh sách Hóa đơn");

        // Tạo dòng Tiêu đề (Header)
        Row headerRow = sheet.createRow(0);
        String[] columns = {"Mã HĐ", "Khách hàng", "Số điện thoại", "Ngày tạo", "Trạng thái", "Tổng tiền"};
        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
        }

        // Đổ dữ liệu vào các dòng
        int rowIdx = 1;
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        for (Order order : invoices) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue("#" + order.getId());
            row.createCell(1).setCellValue(order.getCustomerName());
            row.createCell(2).setCellValue(order.getCustomerPhone());
            row.createCell(3).setCellValue(sdf.format(order.getOrderDate()));
            row.createCell(4).setCellValue(order.getStatus());
            row.createCell(5).setCellValue(order.getTotalAmount());
        }

        // Căn chỉnh độ rộng cột tự động
        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }

        // Ghi ra luồng phản hồi
        workbook.write(response.getOutputStream());
        workbook.close();
    }
    // 3. XỬ LÝ CẬP NHẬT HÓA ĐƠN
    @PostMapping("/update")
    public String updateInvoice(
            @RequestParam("invoiceId") Long invoiceId,
            @RequestParam("customerName") String customerName,
            @RequestParam("customerPhone") String customerPhone,
            @RequestParam("address") String address,
            @RequestParam("totalAmount") Double totalAmount,
            @RequestParam("status") String status,
            org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        try {
            orderService.updateInvoiceDetails(invoiceId, customerName, customerPhone, address, totalAmount, status);
            ra.addFlashAttribute("success", "Cập nhật hóa đơn #" + invoiceId + " thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Lỗi khi cập nhật: " + e.getMessage());
        }
        return "redirect:/admin/invoices";
    }

    // 4. XỬ LÝ XÓA HÓA ĐƠN
    @PostMapping("/delete")
    public String deleteInvoice(@RequestParam("invoiceId") Long invoiceId, org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        try {
            orderService.deleteInvoiceAndDetails(invoiceId);
            ra.addFlashAttribute("success", "Đã xóa vĩnh viễn hóa đơn #" + invoiceId + " cùng các chứng từ liên quan!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Lỗi khi xóa hóa đơn: " + e.getMessage());
        }
        return "redirect:/admin/invoices";
    }
}