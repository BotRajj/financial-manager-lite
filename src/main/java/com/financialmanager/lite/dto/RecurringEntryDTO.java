package com.financialmanager.lite.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class RecurringEntryDTO {
    private String description;
    private BigDecimal amount;
    private String type;
    private String category;
    @Builder.Default
    private String direction = "STANDARD";
    private String personName;
    @Builder.Default
    private String frequency = "MONTHLY";
    @Builder.Default
    private int dayOfMonth = 1;
    private Integer monthOfYear;
    private Long accountId;
    private Long creditCardId;
    private boolean autoApply;
}
