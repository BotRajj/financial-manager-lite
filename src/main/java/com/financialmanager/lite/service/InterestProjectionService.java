package com.financialmanager.lite.service;

import com.financialmanager.lite.model.Entry;
import org.springframework.stereotype.Service;

import com.financialmanager.lite.model.EntryInstallment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class InterestProjectionService {

    public record InstallmentFeeResult(
            long daysLate,
            BigDecimal originalAmount,
            BigDecimal lateFeeAmount,
            BigDecimal interestAmount,
            BigDecimal totalDue
    ) {
        public boolean isOverdue() { return daysLate > 0; }
        public boolean hasFees() { return totalDue.compareTo(originalAmount) > 0; }
    }

    public InstallmentFeeResult calculateInstallmentFee(EntryInstallment inst, Entry entry) {
        BigDecimal original = inst.getAmount();
        if (Boolean.TRUE.equals(inst.getConfirmed()) || inst.getDueDate() == null) {
            return new InstallmentFeeResult(0, original, BigDecimal.ZERO, BigDecimal.ZERO, original);
        }
        LocalDate today = LocalDate.now();
        LocalDate effectiveDueDate = entry.getCreditCard() != null
                ? entry.getCreditCard().invoiceDueDateFor(inst.getDueDate())
                : inst.getDueDate();
        long daysLate = Math.max(0, ChronoUnit.DAYS.between(effectiveDueDate, today));
        if (daysLate == 0 || (entry.getInterestRate() == null && entry.getLateFeeRate() == null)) {
            return new InstallmentFeeResult(daysLate, original, BigDecimal.ZERO, BigDecimal.ZERO, original);
        }
        BigDecimal lateRate    = entry.getLateFeeRate()   != null ? entry.getLateFeeRate()   : BigDecimal.ZERO;
        BigDecimal monthlyRate = entry.getInterestRate()  != null ? entry.getInterestRate()  : BigDecimal.ZERO;
        BigDecimal lateFeeAmt  = original.multiply(lateRate).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal base        = original.add(lateFeeAmt);
        double dailyRate = Math.pow(1.0 + monthlyRate.doubleValue(), 1.0 / 30.0) - 1.0;
        double totalD    = base.doubleValue() * Math.pow(1.0 + dailyRate, daysLate);
        BigDecimal totalDue     = BigDecimal.valueOf(totalD).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal interestAmt  = totalDue.subtract(base);
        return new InstallmentFeeResult(daysLate, original, lateFeeAmt, interestAmt, totalDue);
    }

    public record ProjectionResult(
            BigDecimal principal,
            BigDecimal lateFeeAmount,
            BigDecimal baseAmount,
            LocalDate defaultSinceDate,
            BigDecimal monthlyRate,
            Map<LocalDate, BigDecimal> projection
    ) {}

    /**
     * Projeta o valor total devido para cada dia a partir da data de inadimplência
     * até {@code months} meses à frente.
     * Fórmula:
     *   base = principal × (1 + lateFeeRate)          — multa aplicada uma vez
     *   dailyRate = (1 + monthlyRate)^(1/30) − 1
     *   total(d) = base × (1 + dailyRate)^dias_em_atraso
     */
    public ProjectionResult project(Entry entry, int months) {
        BigDecimal principal   = entry.getRemainingAmount();
        BigDecimal monthlyRate = entry.getInterestRate()  != null ? entry.getInterestRate()  : BigDecimal.ZERO;
        BigDecimal lateFeeRate = entry.getLateFeeRate()   != null ? entry.getLateFeeRate()   : BigDecimal.ZERO;
        LocalDate  since       = entry.getDefaultSinceDate() != null ? entry.getDefaultSinceDate() : LocalDate.now();

        BigDecimal lateFeeAmount = principal.multiply(lateFeeRate).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal base          = principal.add(lateFeeAmount);

        double monthlyRateD = monthlyRate.doubleValue();
        double dailyRate    = Math.pow(1.0 + monthlyRateD, 1.0 / 30.0) - 1.0;

        LocalDate today = LocalDate.now();
        LocalDate end   = today.plusMonths(months);

        Map<LocalDate, BigDecimal> projection = new LinkedHashMap<>();
        for (LocalDate d = since; !d.isAfter(end); d = d.plusDays(1)) {
            long daysLate = ChronoUnit.DAYS.between(since, d);
            double total  = base.doubleValue() * Math.pow(1.0 + dailyRate, daysLate);
            projection.put(d, BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_EVEN));
        }

        return new ProjectionResult(principal, lateFeeAmount, base, since, monthlyRate, projection);
    }
}
