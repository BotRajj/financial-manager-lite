package com.financialmanager.lite.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "spending_projection_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpendingProjectionItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projection_id", nullable = false)
    private SpendingProjection projection;

    /** ENTRY | CARD | CUSTOM */
    @Column(name = "item_type", nullable = false, length = 20)
    private String itemType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id")
    private Entry entry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    private CreditCard card;

    /** MIN_PAYMENT | INSTALLMENT */
    @Column(name = "card_mode", length = 20)
    private String cardMode;

    @Column(name = "card_installments")
    private Integer cardInstallments;

    @Column(name = "card_entrada", precision = 15, scale = 2)
    private BigDecimal cardEntrada;

    @Column(name = "card_day_of_month")
    private Integer cardDayOfMonth;

    @Column(name = "custom_description", length = 200)
    private String customDescription;

    @Column(name = "custom_amount", precision = 15, scale = 2)
    private BigDecimal customAmount;

    @Column(name = "custom_installments", nullable = false)
    @Builder.Default
    private Integer customInstallments = 1;

    @Column(name = "custom_day_of_month", nullable = false)
    @Builder.Default
    private Integer customDayOfMonth = 1;

    /** EXPENSE | INCOME */
    @Column(name = "custom_type", nullable = false, length = 10)
    @Builder.Default
    private String customType = "EXPENSE";

    /** true = excluído (usado no modo EXCLUDE_FROM_ALL para ENTRY items) */
    @Column(nullable = false)
    @Builder.Default
    private boolean excluded = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean confirmed = false;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    // ── helpers ────────────────────────────────────────────────────────────

    public String getDisplayLabel() {
        return switch (itemType != null ? itemType : "") {
            case "ENTRY"  -> entry != null ? entry.getDescription() : "—";
            case "CARD"   -> card != null
                    ? card.getName() + " — " + ("INSTALLMENT".equals(cardMode)
                        ? cardInstallments + "x" : "Pag. Mínimo")
                    : "—";
            case "CUSTOM" -> customDescription != null ? customDescription : "—";
            default       -> "—";
        };
    }

    public BigDecimal getStaticMonthlyValue() {
        if ("ENTRY".equals(itemType) && entry != null) return entry.getInstallmentValue();
        if ("CUSTOM".equals(itemType) && customAmount != null) return customAmount;
        return BigDecimal.ZERO;
    }

    public BigDecimal calcCardPmt() {
        if (!"CARD".equals(itemType) || !"INSTALLMENT".equals(cardMode) || card == null) return BigDecimal.ZERO;
        int n = cardInstallments != null && cardInstallments > 0 ? cardInstallments : 1;
        BigDecimal entrada = cardEntrada != null ? cardEntrada : BigDecimal.ZERO;
        BigDecimal pv = card.getUsedAmount().subtract(entrada);
        if (pv.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal rate = card.getInstallmentRate();
        if (rate.compareTo(BigDecimal.ZERO) == 0) {
            return pv.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_EVEN);
        }
        BigDecimal onePlusRN = BigDecimal.ONE.add(rate).pow(n);
        BigDecimal factor = BigDecimal.ONE.divide(onePlusRN, 10, RoundingMode.HALF_EVEN);
        return pv.multiply(rate).divide(BigDecimal.ONE.subtract(factor), 2, RoundingMode.HALF_EVEN);
    }
}
