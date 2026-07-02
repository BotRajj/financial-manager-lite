package com.financialmanager.lite.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class SimpleEntryDTO {
    private String description;
    private BigDecimal amount;
    private String type;
    private String category;
    private LocalDate entryDate;
    private Long accountId;
    private Long creditCardId;
    private String notes;
    @Builder.Default
    private String direction = "STANDARD";
    private String personName;
    private String invoiceMonth;
}
