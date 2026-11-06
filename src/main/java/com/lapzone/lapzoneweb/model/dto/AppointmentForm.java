package com.lapzone.lapzoneweb.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public class AppointmentForm {

    @NotBlank(message = "Vui lòng nhập họ và tên.")
    @Size(max = 100, message = "Họ và tên không được vượt quá 100 ký tự.")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập số điện thoại.")
    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự.")
    private String phone;

    @NotBlank(message = "Vui lòng nhập email.")
    @Email(message = "Email không đúng định dạng.")
    @Size(max = 150, message = "Email không được vượt quá 150 ký tự.")
    private String email;

    @NotBlank(message = "Vui lòng chọn loại hỗ trợ.")
    @Size(max = 100, message = "Loại hỗ trợ không hợp lệ.")
    private String supportType;

    @Size(max = 200, message = "Showroom không được vượt quá 200 ký tự.")
    private String showroom;

    @NotNull(message = "Vui lòng chọn ngày hẹn.")
    private LocalDate appointmentDate;

    @NotNull(message = "Vui lòng chọn giờ hẹn.")
    private LocalTime appointmentTime;

    @Size(max = 5000, message = "Nội dung hỗ trợ không được vượt quá 5000 ký tự.")
    private String description;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSupportType() { return supportType; }
    public void setSupportType(String supportType) { this.supportType = supportType; }
    public String getShowroom() { return showroom; }
    public void setShowroom(String showroom) { this.showroom = showroom; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public LocalTime getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(LocalTime appointmentTime) { this.appointmentTime = appointmentTime; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
