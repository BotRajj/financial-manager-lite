package com.financialmanager.lite.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "credit_cards")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreditCard extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "credit_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "used_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal usedAmount = BigDecimal.ZERO;

    @Column(name = "closing_day", nullable = false)
    private int closingDay;

    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Column(name = "minimum_payment_rate", nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal minimumPaymentRate = new BigDecimal("0.1500");

    @Column(name = "rotating_credit_rate", nullable = false, precision = 7, scale = 4)
    @Builder.Default
    private BigDecimal rotatingCreditRate = new BigDecimal("0.1500");

    @Column(name = "installment_rate", nullable = false, precision = 7, scale = 4)
    @Builder.Default
    private BigDecimal installmentRate = new BigDecimal("0.1590");

    @Column(name = "late_payment_fine", nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal latePaymentFine = new BigDecimal("0.0200");

    @Column(name = "late_payment_interest", nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal latePaymentInterest = new BigDecimal("0.0100");

    @Column(name = "iof_daily_rate", nullable = false, precision = 8, scale = 6)
    @Builder.Default
    private BigDecimal iofDailyRate = new BigDecimal("0.000082");

    @Column(name = "iof_additional_rate", nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal iofAdditionalRate = new BigDecimal("0.0038");

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "creditCard", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CardNumber> cardNumbers = new ArrayList<>();

    public BigDecimal getAvailableLimit() {
        return creditLimit.subtract(usedAmount);
    }

    public BigDecimal getMinimumPayment() {
        return usedAmount.multiply(minimumPaymentRate).setScale(2, java.math.RoundingMode.HALF_EVEN);
    }

    public double getUsagePercentage() {
        if (creditLimit.compareTo(BigDecimal.ZERO) == 0) return 0;
        return usedAmount.divide(creditLimit, 4, java.math.RoundingMode.HALF_EVEN)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    public YearMonth closingMonthFor(LocalDate date) {
        YearMonth ym = YearMonth.from(date);
        int effectiveClosing = Math.min(closingDay, ym.lengthOfMonth());
        return date.getDayOfMonth() <= effectiveClosing ? ym : ym.plusMonths(1);
    }

    public LocalDate invoiceDueDateFor(LocalDate paymentDate) {
        YearMonth closingMonth = closingMonthFor(paymentDate);
        YearMonth dueMonth = dueDay > closingDay ? closingMonth : closingMonth.plusMonths(1);
        return dueMonth.atDay(Math.min(dueDay, dueMonth.lengthOfMonth()));
    }

    public LocalDate nextDueDate() {
        LocalDate currentDue = billingPeriod(0)[2];
        return LocalDate.now().isAfter(currentDue) ? billingPeriod(1)[2] : currentDue;
    }

    public LocalDate[] billingPeriod(int monthOffset) {
        YearMonth closingMonth = closingMonthFor(LocalDate.now()).plusMonths(monthOffset);
        LocalDate periodEnd   = closingMonth.atDay(Math.min(closingDay, closingMonth.lengthOfMonth()));
        YearMonth prevMonth   = closingMonth.minusMonths(1);
        LocalDate periodStart = prevMonth.atDay(Math.min(closingDay, prevMonth.lengthOfMonth())).plusDays(1);
        YearMonth dueMonth    = dueDay > closingDay ? closingMonth : closingMonth.plusMonths(1);
        LocalDate dueDate     = dueMonth.atDay(Math.min(dueDay, dueMonth.lengthOfMonth()));
        return new LocalDate[]{periodStart, periodEnd, dueDate};
    }
}
