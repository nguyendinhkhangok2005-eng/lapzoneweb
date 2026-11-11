package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.config.StoreDirectoryProperties;
import com.lapzone.lapzoneweb.model.service.VoucherCatalogService;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StoreDirectoryController {

    private final StoreDirectoryProperties storeDirectory;
    private final VoucherCatalogService voucherCatalog;

    public StoreDirectoryController(StoreDirectoryProperties storeDirectory, VoucherCatalogService voucherCatalog) {
        this.storeDirectory = storeDirectory;
        this.voucherCatalog = voucherCatalog;
    }

    @GetMapping("/store")
    public String showStoreDashboard(Model model, Authentication authentication) {
        CustomUserDetails details = authentication != null && authentication.getPrincipal() instanceof CustomUserDetails user
                ? user : null;
        model.addAttribute("availableVouchers", voucherCatalog.availableFor(details == null ? null : details.getUser()));
        model.addAttribute("storeBranches", storeDirectory.getBranches().stream()
                .filter(branch -> branch != null)
                .filter(StoreDirectoryProperties.Branch::hasInformation)
                .toList());
        return "store";
    }
}
