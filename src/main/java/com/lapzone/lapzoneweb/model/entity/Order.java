package com.lapzone.lapzoneweb.model.entity;
import java.util.List;
import jakarta.persistence.OneToMany;
import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "orders") // Thêm 's' vì chữ 'order' là từ khóa nhạy cảm trong SQL
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "total_amount")
    private Double totalAmount;
    
    private String status; // Ví dụ: "CHỜ XÁC NHẬN", "ĐANG GIAO", "HOÀN THÀNH"
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "order_date")
    private Date orderDate;
    
    @Column(name = "customer_name")
    private String customerName;
    
    @Column(name = "customer_phone")
    private String customerPhone;
    
    private String address;
    private String paymentMethod; // "COD", "VNPAY", hoặc "MOMO"

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    // QUAN TRỌNG: Mapping với bảng User thay vì dùng Long userId
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    public Order() {}
    // Thêm danh sách chi tiết đơn hàng để giao diện Hóa đơn có thể lấy được dữ liệu
    @OneToMany(mappedBy = "order")
    private List<OrderDetail> orderDetails;

    public List<OrderDetail> getOrderDetails() {
        return orderDetails;
    }

    public void setOrderDetails(List<OrderDetail> orderDetails) {
        this.orderDetails = orderDetails;
    }
    // Getters và Setters (Bạn giữ nguyên các hàm get/set, chỉ đổi getUserId() thành getUser() nhé)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getOrderDate() { return orderDate; }
    public void setOrderDate(Date orderDate) { this.orderDate = orderDate; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}