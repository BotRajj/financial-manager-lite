package com.financialmanager.lite.dto;

import lombok.Data;

import java.math.BigDecimal;

/** Captura os campos do formulário de edição de parcelamentos. */
@Data
public class InstallmentEditFormDTO {
    private String description;
    private BigDecimal amount;
    private String type;
    private String category;
    private String personName;
    private String accountId;
    private String creditCardId;
    private Integer dayOfMonth;
    private String installmentStartDate;
    private String redirectTab = "parcelas";
}
