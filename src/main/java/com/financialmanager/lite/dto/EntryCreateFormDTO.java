package com.financialmanager.lite.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Captura todos os campos do formulário de criação de movimentações (todos os modos). */
@Data
public class EntryCreateFormDTO {

    // ── Modo ──────────────────────────────────────────────────────────────
    private String mode = "SIMPLE";

    // ── Campos comuns ─────────────────────────────────────────────────────
    private String description;
    private BigDecimal amount;
    private String type = "EXPENSE";
    private String category;
    private String notes;
    private String personName;
    private String accountId;
    private String creditCardId;

    // ── Me Devem ──────────────────────────────────────────────────────────
    private boolean meDevem;
    private String receiveDate;
    private String receiveAccountId;

    // ── SIMPLE ────────────────────────────────────────────────────────────
    private String entryDate;
    private String invoiceMonth;

    // ── RECURRING / INSTALLMENT ───────────────────────────────────────────
    private String frequency;
    private Integer dayOfMonth;
    private Integer monthOfYear;

    // ── RECURRING only ────────────────────────────────────────────────────
    private boolean autoApply;

    // ── INSTALLMENT only ──────────────────────────────────────────────────
    private Integer totalInstallments;
    private List<BigDecimal> customAmounts;
    private String installmentStartDate;
    private int alreadyPaidInstallments = 0;
    private BigDecimal interestRate;
    private BigDecimal lateFeeRate;
}
