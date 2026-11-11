package com.lapzone.lapzoneweb.model.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter @Setter
public class VoucherForm {
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,40}", message = "Mã gồm 3–40 chữ cái, chữ số, dấu gạch ngang hoặc gạch dưới.")
    private String code;
    @Min(1) @Max(10) private int discountPercent = 1;
    @Min(1) @Max(1000000) private int quantity = 1;
    @Min(1) @Max(100) private int perUserLimit = 1;
    private boolean active = true;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startsAt;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endsAt;
    @NotNull @DecimalMin("0") @DecimalMax("1000000000000") @Digits(integer = 13, fraction = 2)
    private BigDecimal minimumOrderValue = BigDecimal.ZERO;
    @Size(max = 500) private Set<@Positive Long> productIds = new HashSet<>();
    @Size(max = 500) private Set<@Positive Long> categoryIds = new HashSet<>();
    @Size(max = 500) private Set<@Positive Long> userIds = new HashSet<>();
}
