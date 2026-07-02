package com.financialmanager.lite.dto;

import lombok.Data;

import java.math.BigDecimal;

/** Captura os campos do formulário de edição de lançamentos simples. */
@Data
public class EntryEditFormDTO {
    private String description;
    private BigDecimal amount;
    private String type;
    private String category;
    private String entryDate;
    private String accountId;
    private String creditCardId;
    private String notes;
    private String invoiceMonth;
}
