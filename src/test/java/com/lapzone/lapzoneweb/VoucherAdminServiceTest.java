package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.model.dto.VoucherForm;
import com.lapzone.lapzoneweb.model.entity.Voucher;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.model.service.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherAdminServiceTest {
    @Mock VoucherRepository vouchers;
    @Mock VoucherRedemptionRepository redemptions;
    @Mock ProductRepository products;
    @Mock CategoryRepository categories;
    @Mock UserRepository users;
    ValidatorFactory factory;
    VoucherAdminService service;

    @BeforeEach void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        service = new VoucherAdminService(vouchers, redemptions, products, categories, users, factory.getValidator());
    }
    @AfterEach void close() { factory.close(); }
    VoucherForm form() {
        VoucherForm f = new VoucherForm(); f.setCode("save10"); f.setDiscountPercent(10); f.setQuantity(10);
        f.setStartsAt(LocalDateTime.now(VoucherService.ZONE)); f.setEndsAt(f.getStartsAt().plusDays(1));
        return f;
    }
    @Test void rejectsOutOfRangePercentInvalidDatesAndNegativeMinimum() {
        VoucherForm f = form(); f.setDiscountPercent(11);
        assertThrows(IllegalArgumentException.class, () -> service.save(null, f));
        f.setDiscountPercent(0);
        assertThrows(IllegalArgumentException.class, () -> service.save(null, f));
        f.setDiscountPercent(1); f.setEndsAt(f.getStartsAt());
        assertThrows(IllegalArgumentException.class, () -> service.save(null, f));
        f.setEndsAt(f.getStartsAt().plusDays(1)); f.setMinimumOrderValue(java.math.BigDecimal.valueOf(-1));
        assertThrows(IllegalArgumentException.class, () -> service.save(null, f));
        verifyNoInteractions(vouchers);
    }
    @Test void createNormalizesCodeAndIgnoresAnyClientCounterFields() {
        service.save(null, form());
        verify(vouchers).saveAndFlush(argThat(v -> v.getCode().equals("SAVE10") && v.getReservedCount() == 0 && v.getRedeemedCount() == 0));
    }
    @Test void duplicateCodeAndUnknownEligibilityReferencesAreRejected() {
        when(vouchers.existsByCode("SAVE10")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.save(null, form()));
        when(vouchers.existsByCode("SAVE10")).thenReturn(false);
        VoucherForm f = form(); f.setProductIds(Set.of(999L));
        assertThrows(IllegalArgumentException.class, () -> service.save(null, f));
        verify(vouchers, never()).saveAndFlush(any());
    }
    @Test void editingCannotReduceCapacityBelowAllocatedAndDeactivationPreservesCounts() {
        Voucher v = new Voucher(); v.setId(1L); v.setReservedCount(2); v.setRedeemedCount(3);
        when(vouchers.lockById(1L)).thenReturn(Optional.of(v));
        VoucherForm f = form(); f.setQuantity(4);
        assertThrows(IllegalArgumentException.class, () -> service.save(1L, f));
        service.deactivate(1L);
        assertFalse(v.isActive()); assertEquals(2, v.getReservedCount()); assertEquals(3, v.getRedeemedCount());
    }
}
