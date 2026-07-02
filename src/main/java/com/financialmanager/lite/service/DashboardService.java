package com.financialmanager.lite.service;

import com.financialmanager.lite.model.CreditCard;
import com.financialmanager.lite.model.User;
import com.financialmanager.lite.repository.AccountRepository;
import com.financialmanager.lite.repository.CreditCardRepository;
import com.financialmanager.lite.repository.EntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AccountRepository accountRepository;
    private final CreditCardRepository creditCardRepository;
    private final EntryRepository entryRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(User user) {
        YearMonth current = YearMonth.now();
        LocalDate from = current.atDay(1);
        LocalDate to   = current.atEndOfMonth();

        BigDecimal totalBalance  = accountRepository.sumBalanceByUser(user);
        BigDecimal monthIncome   = entryRepository.sumSimpleByUserAndTypeAndDateRange(user, "INCOME",  from, to);
        BigDecimal monthExpenses = entryRepository.sumSimpleByUserAndTypeAndDateRange(user, "EXPENSE", from, to);
        BigDecimal totalDebt     = entryRepository.sumRemainingPayablesByUser(user);
        BigDecimal totalReceivable = entryRepository.sumRemainingReceivablesByUser(user);

        BigDecimal totalCardUsed = creditCardRepository.findByUserAndActiveTrue(user).stream()
                .map(CreditCard::getUsedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal effectiveBalance = totalBalance.subtract(totalCardUsed);

        BigDecimal savings = monthIncome.subtract(monthExpenses);
        double savingsRate = monthIncome.compareTo(BigDecimal.ZERO) > 0
                ? savings.divide(monthIncome, 4, java.math.RoundingMode.HALF_EVEN)
                         .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        Map<String, BigDecimal> expenseByCategory = new LinkedHashMap<>();
        for (Object[] row : entryRepository.sumExpensesByCategory(user, from, to)) {
            expenseByCategory.put((String) row[0], (BigDecimal) row[1]);
        }

        long activeDebts      = entryRepository.findActivePayablesByUser(user).size();
        long activeReceivables = entryRepository.findActiveReceivablesByUser(user).size();
        long activeCards      = creditCardRepository.findByUserAndActiveTrue(user).size();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalBalance",      totalBalance);
        summary.put("totalCardUsed",     totalCardUsed);
        summary.put("effectiveBalance",  effectiveBalance);
        summary.put("totalReceivable",   totalReceivable);
        summary.put("monthIncome",       monthIncome);
        summary.put("monthExpenses",     monthExpenses);
        summary.put("savings",           savings);
        summary.put("savingsRate",       String.format(Locale.US, "%.1f%%", savingsRate));
        summary.put("totalDebt",         totalDebt);
        summary.put("activeDebts",       activeDebts);
        summary.put("activeReceivables", activeReceivables);
        summary.put("activeCards",       activeCards);
        summary.put("expenseByCategory", expenseByCategory);
        summary.put("period",            current.toString());
        return summary;
    }
}
