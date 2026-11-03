package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    // Tìm tất cả đơn hàng của một khách hàng (dùng cho trang Lịch sử mua hàng sau này)
    List<Order> findByUserId(Long userId);
    // [CŨ - giữ lại để tương thích, nhưng CHỈ dùng cho export/in báo cáo]
    @Query("SELECT o FROM Order o WHERE " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:keyword IS NULL OR o.customerName LIKE %:keyword% OR o.customerPhone LIKE %:keyword% OR CAST(o.id AS string) LIKE %:keyword%) AND " +
           "(:startDate IS NULL OR o.orderDate >= :startDate) AND " +
           "(:endDate IS NULL OR o.orderDate <= :endDate) " +
           "ORDER BY o.orderDate DESC")
    List<Order> searchOrders(@Param("keyword") String keyword, @Param("status") String status, @Param("startDate") java.util.Date startDate, @Param("endDate") java.util.Date endDate);

    // [MỚI - FIX HIỆU SUẤT] Phân trang: chỉ load 20 bản ghi/trang thay vì toàn bộ 500.000
    @Query("SELECT o FROM Order o WHERE " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:keyword IS NULL OR o.customerName LIKE %:keyword% OR o.customerPhone LIKE %:keyword% OR CAST(o.id AS string) LIKE %:keyword%) AND " +
           "(:startDate IS NULL OR o.orderDate >= :startDate) AND " +
           "(:endDate IS NULL OR o.orderDate <= :endDate)")
    Page<Order> searchOrdersPaged(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("startDate") java.util.Date startDate,
            @Param("endDate") java.util.Date endDate,
            Pageable pageable
    );
    // Lọc những đơn có trạng thái cụ thể (truyền vào List các trạng thái)
    List<Order> findByStatusInOrderByOrderDateDesc(List<String> statuses);

    // [CŨ - giữ cho export/in báo cáo - cần toàn bộ dữ liệu trong khoảng ngày]
    @Query("SELECT o FROM Order o WHERE o.status IN ('ĐÃ THANH TOÁN', 'HOÀN THÀNH') AND " +
           "(:startDate IS NULL OR o.orderDate >= :startDate) AND " +
           "(:endDate IS NULL OR o.orderDate <= :endDate) " +
           "ORDER BY o.orderDate DESC")
    List<Order> findValidInvoicesBetweenDates(@Param("startDate") java.util.Date startDate, @Param("endDate") java.util.Date endDate);

    // [MỚI - FIX HIỆU SUẤT] Phân trang cho trang danh sách hóa đơn
    @Query("SELECT o FROM Order o WHERE o.status IN ('ĐÃ THANH TOÁN', 'HOÀN THÀNH') AND " +
           "(:startDate IS NULL OR o.orderDate >= :startDate) AND " +
           "(:endDate IS NULL OR o.orderDate <= :endDate)")
    Page<Order> findValidInvoicesPaged(
            @Param("startDate") java.util.Date startDate,
            @Param("endDate") java.util.Date endDate,
            Pageable pageable
    );

    // Tính tổng doanh thu của THÁNG HIỆN TẠI (Chỉ tính đơn đã thanh toán/hoàn thành)
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status IN ('ĐÃ THANH TOÁN', 'HOÀN THÀNH') AND MONTH(o.orderDate) = MONTH(CURRENT_DATE) AND YEAR(o.orderDate) = YEAR(CURRENT_DATE)")
    Double calculateCurrentMonthRevenue();

    // Tìm đơn hàng trong khoảng thời gian
    @Query("SELECT o FROM Order o WHERE o.status IN ('ĐÃ THANH TOÁN', 'HOÀN THÀNH') AND o.orderDate >= :startDate AND o.orderDate <= :endDate ORDER BY o.orderDate DESC")
    List<Order> findCompletedOrdersBetweenDates(@Param("startDate") java.util.Date startDate, @Param("endDate") java.util.Date endDate);

    // Tổng doanh thu trong khoảng thời gian
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status IN ('ĐÃ THANH TOÁN', 'HOÀN THÀNH') AND o.orderDate >= :startDate AND o.orderDate <= :endDate")
    Double calculateRevenueBetweenDates(@Param("startDate") java.util.Date startDate, @Param("endDate") java.util.Date endDate);
}