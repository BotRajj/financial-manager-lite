package com.financialmanager.lite.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "entry_installments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"entry_id", "installment_number"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EntryInstallment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    private Entry entry;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    @Builder.Default
    private Boolean confirmed = false;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;
}
