package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ReturnRequestService {
    private final OrderRepository orders;
    private final OrderDetailRepository details;
    private final OrderReturnCaseRepository requests;
    private final OrderReturnEventRepository events;
    private final OrderAuthorization authorization;

    public ReturnRequestService(OrderRepository orders, OrderDetailRepository details, OrderReturnCaseRepository requests,
                                OrderReturnEventRepository events, OrderAuthorization authorization) {
        this.orders = orders; this.details = details; this.requests = requests; this.events = events; this.authorization = authorization;
    }
    public record EligibleItem(Long orderId, Long itemId, String productName, String productImage, int maximumQuantity, Double unitPrice) {}
    public record EventView(String status, String note, LocalDateTime createdAt) {}
    public record RequestView(Long id, Long orderId, Long itemId, String customerName, String productName, Integer quantity,
                              String type, String status, String reason, LocalDateTime createdAt, String adminNote,
                              LocalDateTime reviewedAt, String completionNote, LocalDateTime completedAt) {}

    public EligibleItem eligibleItem(Long orderId, Long itemId) {
        User owner = authorization.requireUser();
        Order order = orders.findByIdAndUserId(orderId, owner.getId()).orElseThrow(this::missing);
        OrderDetail line = details.findByIdAndOrderId(itemId, orderId).orElseThrow(this::missing);
        checkEligible(order, line, requests.findByOrderIdIn(List.of(orderId)));
        Product product = line.getProduct();
        return new EligibleItem(orderId, itemId, product == null ? "Sản phẩm đã mua" : product.getName(),
                product == null ? null : product.getImage(), line.getQuantity(), line.getPrice());
    }

    @Transactional
    public RequestView submit(Long orderId, Long itemId, Integer quantity, String type, String reason) {
        User owner = authorization.requireUser();
        Order order = orders.lockOwnedOrder(orderId, owner.getId()).orElseThrow(this::missing);
        OrderDetail line = details.findByIdAndOrderId(itemId, orderId).orElseThrow(this::missing);
        // A current locking read also sees requests committed while waiting for the order lock on MySQL.
        checkEligible(order, line, requests.lockByOrderId(orderId));
        if (quantity == null || quantity < 1 || quantity > line.getQuantity())
            throw new IllegalArgumentException("Số lượng yêu cầu phải từ 1 đến số lượng sản phẩm đã mua.");
        OrderReturnCase.Type kind;
        try { kind = OrderReturnCase.Type.valueOf(type.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Loại yêu cầu không hợp lệ."); }
        String body = text(reason, true, "Lý do");
        OrderReturnCase request = new OrderReturnCase();
        request.setOrder(order); request.setOrderDetail(line); request.setQuantity(quantity); request.setType(kind);
        request.setReason(body); request.setCreatedAt(now()); requests.saveAndFlush(request);
        recordEvent(request, owner, null, "Khách hàng gửi yêu cầu.");
        return view(request);
    }

    public Page<RequestView> adminList(int page, String status) {
        authorization.requireAdmin();
        if (page < 0 || page > 100000) throw new IllegalArgumentException("Trang không hợp lệ.");
        var pageable = PageRequest.of(page, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<OrderReturnCase> result = status == null || status.isBlank() ? requests.findAll(pageable)
                : requests.findByStatus(status(status), pageable);
        return result.map(this::view);
    }

    public List<EventView> history(Long requestId) {
        User actor = authorization.requireUser();
        OrderReturnCase request = requests.findById(requestId).orElseThrow(this::missing);
        boolean owner = request.getOrder().getUser() != null && actor.getId().equals(request.getOrder().getUser().getId());
        if (!owner) {
            try { authorization.requireAdmin(); }
            catch (org.springframework.security.access.AccessDeniedException e) { throw missing(); }
        }
        return events.findByRequestIdOrderByIdAsc(requestId).stream().map(e -> new EventView(e.getNewStatus().name(), e.getNote(), e.getCreatedAt())).toList();
    }

    @Transactional
    public RequestView decide(Long id, String target, String note, boolean manualProcessingConfirmed) {
        User admin = authorization.requireAdmin();
        OrderReturnCase.Status next = status(target);
        if (!Set.of(OrderReturnCase.Status.APPROVED, OrderReturnCase.Status.REJECTED, OrderReturnCase.Status.COMPLETED).contains(next))
            throw new IllegalArgumentException("Thao tác xử lý yêu cầu không hợp lệ.");
        Long orderId = requests.orderIdForRequest(id).orElseThrow(this::missing);
        // Always lock the order before the case, matching submission and existing order status updates.
        orders.findByIdForUpdate(orderId).orElseThrow(this::missing);
        OrderReturnCase request = requests.lockById(id).orElseThrow(this::missing);
        if (request.getStatus() == next) return view(request);
        boolean permitted = request.getStatus() == OrderReturnCase.Status.REQUESTED
                && Set.of(OrderReturnCase.Status.APPROVED, OrderReturnCase.Status.REJECTED).contains(next)
                || request.getStatus() == OrderReturnCase.Status.APPROVED && next == OrderReturnCase.Status.COMPLETED;
        if (!permitted) throw new IllegalArgumentException("Không thể chuyển yêu cầu sang trạng thái đã chọn.");
        String message = text(note, next != OrderReturnCase.Status.APPROVED, "Ghi chú xử lý");
        if (next == OrderReturnCase.Status.COMPLETED && !manualProcessingConfirmed)
            throw new IllegalArgumentException("Vui lòng xác nhận đã đối soát và xử lý hồ sơ ngoài hệ thống.");
        OrderReturnCase.Status previous = request.getStatus();
        if (next == OrderReturnCase.Status.COMPLETED) {
            request.setCompletedBy(admin); request.setCompletedAt(now()); request.setCompletionNote(message);
        } else {
            request.setReviewedBy(admin); request.setReviewedAt(now()); request.setAdminNote(message);
        }
        request.setStatus(next);
        recordEvent(request, admin, previous, message);
        // No payment, voucher, order, stock, or order-detail mutation occurs here.
        return view(request);
    }

    private void checkEligible(Order order, OrderDetail line, List<OrderReturnCase> existing) {
        if (!OrderStatusPolicy.DELIVERED.equals(order.getStatus()))
            throw new IllegalArgumentException("Chỉ sản phẩm trong đơn đã giao thành công mới được gửi yêu cầu.");
        if (line.getQuantity() == null || line.getQuantity() < 1)
            throw new IllegalArgumentException("Số lượng đã mua không hợp lệ. Vui lòng liên hệ hỗ trợ.");
        if (existing.stream().anyMatch(c -> c.getOrderDetail() == null || c.getOrderDetail().getId().equals(line.getId())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sản phẩm này đã có yêu cầu trả hàng hoặc hoàn tiền.");
    }
    private RequestView view(OrderReturnCase request) {
        OrderDetail line = request.getOrderDetail();
        String name = line == null ? "Toàn bộ đơn hàng" : line.getProduct() == null ? "Sản phẩm đã mua" : line.getProduct().getName();
        return new RequestView(request.getId(), request.getOrder().getId(), line == null ? null : line.getId(),
                request.getOrder().getCustomerName(), name, request.getQuantity(), request.getType().name(), request.getStatus().name(),
                request.getReason(), request.getCreatedAt(), request.getAdminNote(), request.getReviewedAt(), request.getCompletionNote(), request.getCompletedAt());
    }
    private void recordEvent(OrderReturnCase request, User actor, OrderReturnCase.Status previous, String note) {
        OrderReturnEvent event = new OrderReturnEvent(); event.setRequest(request); event.setActor(actor);
        event.setPreviousStatus(previous); event.setNewStatus(request.getStatus()); event.setNote(note); event.setCreatedAt(now()); events.save(event);
    }
    private OrderReturnCase.Status status(String value) {
        try { return OrderReturnCase.Status.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Trạng thái yêu cầu không hợp lệ."); }
    }
    private String text(String value, boolean required, String label) {
        String clean = value == null ? "" : value.strip();
        if (required && clean.isEmpty() || clean.length() > 2000
                || clean.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
            throw new IllegalArgumentException(label + " phải có nội dung hợp lệ, tối đa 2.000 ký tự.");
        return clean.isEmpty() ? null : clean;
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu hoặc sản phẩm."); }
}
