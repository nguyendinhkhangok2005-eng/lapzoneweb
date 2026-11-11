package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.dto.VoucherForm;
import com.lapzone.lapzoneweb.model.service.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin/vouchers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVoucherController {
    private final VoucherAdminService service;
    public AdminVoucherController(VoucherAdminService service) { this.service = service; }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, @RequestParam(required = false) Long editId,
                       @RequestParam(required = false) Long historyId, @RequestParam(defaultValue = "0") int historyPage, Model model) {
        VoucherForm form = editId == null ? new VoucherForm() : service.form(editId);
        if (editId == null) {
            form.setStartsAt(LocalDateTime.now(VoucherService.ZONE).withSecond(0).withNano(0));
            form.setEndsAt(form.getStartsAt().plusDays(7));
        }
        model.addAttribute("voucherForm", form); model.addAttribute("editId", editId);
        if (historyId != null) {
            model.addAttribute("history", service.history(historyId, historyPage)); model.addAttribute("historyId", historyId);
        }
        model.addAttribute("voucherPage", service.list(page));
        return "admin/vouchers";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id, @Valid @ModelAttribute("voucherForm") VoucherForm form,
                       BindingResult errors, Model model, RedirectAttributes redirect) {
        try {
            if (errors.hasErrors()) throw new IllegalArgumentException("Thông tin mã giảm giá chưa hợp lệ. Vui lòng kiểm tra các trường.");
            service.save(id, form);
            redirect.addFlashAttribute("success", "Đã lưu mã giảm giá.");
            return "redirect:/admin/vouchers";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            model.addAttribute("error", "Mã đã tồn tại hoặc dữ liệu không hợp lệ. Vui lòng kiểm tra lại.");
        }
        model.addAttribute("editId", id); model.addAttribute("voucherPage", service.list(0));
        return "admin/vouchers";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirect) {
        service.deactivate(id);
        redirect.addFlashAttribute("success", "Đã ngừng mã giảm giá. Các đơn đã đặt vẫn giữ nguyên.");
        return "redirect:/admin/vouchers";
    }
}
