package com.financialmanager.lite.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "budgets",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "category", "period"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Budget extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(name = "limit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal limitAmount;

    @Column(name = "spent_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal spentAmount = BigDecimal.ZERO;

    public BigDecimal getRemainingAmount() {
        BigDecimal r = limitAmount.subtract(spentAmount);
        return r.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : r;
    }

    public double getUsagePercentage() {
        if (limitAmount.compareTo(BigDecimal.ZERO) == 0) return 0;
        return spentAmount.divide(limitAmount, 4, java.math.RoundingMode.HALF_EVEN)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    public boolean isExceeded() {
        return spentAmount.compareTo(limitAmount) > 0;
    }
}
