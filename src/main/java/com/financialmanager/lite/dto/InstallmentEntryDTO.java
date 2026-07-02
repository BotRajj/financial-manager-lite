package com.financialmanager.lite.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class InstallmentEntryDTO {
    private String description;
    private BigDecimal amount;
    private String type;
    private String category;
    @Builder.Default
    private String direction = "STANDARD";
    private String personName;
    private int totalInstallments;
    private BigDecimal installmentAmount;
    private Integer dayOfMonth;
    private Long accountId;
    private Long creditCardId;
    private List<BigDecimal> customAmounts;
    private LocalDate baseDate;
    private BigDecimal contractRate;
    private BigDecimal interestRate;
    private BigDecimal lateFeeRate;
    @Builder.Default
    private int alreadyPaid = 0;
}
