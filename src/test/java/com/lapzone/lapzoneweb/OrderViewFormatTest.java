package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.OrderViewFormat;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class OrderViewFormatTest {
    private final OrderViewFormat display = new OrderViewFormat();

    @Test void formatsStoredOrderValuesForVietnameseDisplayWithoutChangingThem() {
        assertEquals("125.000,5 ₫", display.money(BigDecimal.valueOf(125000.50)));
        assertEquals("12/11/2026 15:30", display.date(Instant.parse("2026-11-12T08:30:00Z")));
        assertEquals("Chưa có giá", display.money(null));
    }

    @Test void resolvesExistingProductImagePathsAndRejectsUnsafeSchemes() {
        assertEquals("/assets/may.jpg", display.imageUrl("may.jpg"));
        assertEquals("/assets/may.jpg", display.imageUrl("assets/may.jpg"));
        assertEquals("/uploads/may.jpg", display.imageUrl("/uploads/may.jpg"));
        assertNull(display.imageUrl("javascript:alert(1)"));
        assertNull(display.imageUrl("../secret.txt"));
        assertNull(display.imageUrl(null));
    }

    @Test void labelsConfirmationAndPaymentSeparately() {
        assertEquals("Chờ xác nhận đơn hàng", display.statusLabel(com.lapzone.lapzoneweb.model.service.OrderStatusPolicy.PENDING));
        assertEquals("Chờ thanh toán", display.statusLabel(com.lapzone.lapzoneweb.model.service.OrderStatusPolicy.WAITING_PAYMENT));
        assertEquals("Đang đóng gói", display.statusLabel("ĐANG ĐÓNG GÓI"));
        assertEquals("Chưa có trạng thái", display.statusLabel(null));
        assertEquals("Thanh toán khi nhận hàng", display.paymentMethod("COD"));
        assertEquals("VNPay", display.paymentMethod("VNPAY"));
    }

    @Test void onlyRecordedPaymentConfirmationShowsPaidAndWaitingForFulfillmentTogether() {
        var paid = display.customerStatus("ĐÃ THANH TOÁN");
        assertEquals("Đã thanh toán", paid.paymentLabel());
        assertEquals("Chờ xác nhận đơn hàng", paid.fulfillmentLabel());
        assertEquals("CHỜ XÁC NHẬN", paid.fulfillmentCode());
        var unpaid = display.customerStatus("CHỜ THANH TOÁN");
        assertEquals("Chờ thanh toán", unpaid.paymentLabel());
        assertNull(unpaid.fulfillmentLabel());
        var failed = display.customerStatus("THANH TOÁN THẤT BẠI");
        assertEquals("Thanh toán thất bại", failed.paymentLabel());
        assertNull(failed.fulfillmentLabel());
    }

    @Test void fulfillmentLabelsNeverInventPaymentOrRefundConfirmation() {
        String[][] cases = {{"CHỜ XÁC NHẬN", "Chờ xác nhận đơn hàng"}, {"ĐANG ĐÓNG GÓI", "Đang đóng gói"},
                {"ĐANG GIAO HÀNG", "Đang giao hàng"}, {"ĐANG GIAO", "Đang giao hàng"},
                {"HOÀN THÀNH", "Hoàn thành"}, {"ĐÃ HỦY", "Đã hủy"}};
        for (String[] expected : cases) {
            var state = display.customerStatus(expected[0]);
            assertEquals(expected[1], state.fulfillmentLabel());
            assertNull(state.paymentLabel());
        }
        assertEquals("Chưa có trạng thái", display.customerStatus(null).fulfillmentLabel());
    }
}
