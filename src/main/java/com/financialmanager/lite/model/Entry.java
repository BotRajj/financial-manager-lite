package com.financialmanager.lite.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "entries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Entry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_card_id")
    private CreditCard creditCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** EXPENSE | INCOME */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(length = 50)
    private String category;

    @Column(length = 500)
    private String notes;

    /** SIMPLE | RECURRING | INSTALLMENT */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String mode = "SIMPLE";

    /** Data da transação — usado em SIMPLE */
    @Column(name = "entry_date")
    private LocalDate entryDate;

    /** Fatura do cartão (yyyy-MM) — quando definido, tem prioridade sobre o cálculo por data */
    @Column(name = "invoice_month", length = 7)
    private String invoiceMonth;

    /** STANDARD | PAYABLE | RECEIVABLE */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String direction = "STANDARD";

    /** Nome do credor (PAYABLE) ou devedor (RECEIVABLE) */
    @Column(name = "person_name", length = 150)
    private String personName;

    /** ACTIVE | SETTLED | CANCELLED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /** Receita RECEIVABLE: foi efetivamente recebida? */
    @Column(nullable = false)
    @Builder.Default
    private Boolean confirmed = false;

    // ── Campos RECURRING ──────────────────────────────────────────────────

    /** MONTHLY | ANNUAL */
    @Column(length = 20)
    private String frequency;

    /** Dia do mês (RECURRING: dia de aplicação; INSTALLMENT: dia de vencimento) */
    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    /** Mês do ano — somente para frequência ANNUAL */
    @Column(name = "month_of_year")
    private Integer monthOfYear;

    /** Último mês aplicado no formato "YYYY-MM" */
    @Column(name = "last_applied_month", length = 7)
    private String lastAppliedMonth;

    /** Soft delete para RECURRING */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** Quando true, o scheduler lança automaticamente no dayOfMonth configurado */
    @Column(name = "auto_apply", nullable = false)
    @Builder.Default
    private Boolean autoApply = false;

    // ── Campos INSTALLMENT ────────────────────────────────────────────────

    @Column(name = "total_installments")
    private Integer totalInstallments;

    @Column(name = "paid_installments", nullable = false)
    @Builder.Default
    private Integer paidInstallments = 0;

    @Column(name = "paid_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /** Valor uniforme por parcela — se nulo, calcula amount / totalInstallments */
    @Column(name = "installment_amount", precision = 15, scale = 2)
    private BigDecimal installmentAmount;

    // ── Campos INADIMPLÊNCIA ──────────────────────────────────────────────

    /** Taxa de juros de mora mensal (ex: 0.1500 = 15% a.m.) */
    @Column(name = "interest_rate", precision = 7, scale = 4)
    private BigDecimal interestRate;

    /** Multa por atraso — cobrada uma única vez (ex: 0.0200 = 2%) */
    @Column(name = "late_fee_rate", precision = 5, scale = 4)
    private BigDecimal lateFeeRate;

    /** Data em que a inadimplência foi confirmada */
    @Column(name = "default_since_date")
    private LocalDate defaultSinceDate;

    /** Taxa de juros contratada no empréstimo (ex: 0.0250 = 2,5% a.m.) */
    @Column(name = "contract_rate", precision = 7, scale = 4)
    private BigDecimal contractRate;

    @OneToMany(mappedBy = "entry", fetch = FetchType.LAZY)
    @OrderBy("installmentNumber ASC")
    @Builder.Default
    private List<EntryInstallment> installments = new ArrayList<>();

    // ── Métodos calculados ─────────────────────────────────────────────────

    public BigDecimal getInstallmentValue() {
        if (installmentAmount != null) return installmentAmount;
        if (totalInstallments == null || totalInstallments == 0) return BigDecimal.ZERO;
        return amount.divide(BigDecimal.valueOf(totalInstallments), 2, RoundingMode.HALF_EVEN);
    }

    public BigDecimal getRemainingAmount() {
        return amount.subtract(paidAmount);
    }

    public int getRemainingInstallments() {
        if (totalInstallments == null) return 0;
        return Math.max(0, totalInstallments - paidInstallments);
    }

    public boolean isOverdue() {
        return "ACTIVE".equals(status) && dayOfMonth != null
                && LocalDate.now().getDayOfMonth() > dayOfMonth;
    }

    public boolean isInDefault() {
        return "IN_DEFAULT".equals(status);
    }
}
