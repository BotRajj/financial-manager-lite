package com.financialmanager.lite.service;

import com.financialmanager.lite.model.CreditCard;
import com.financialmanager.lite.model.Entry;
import com.financialmanager.lite.model.User;
import com.financialmanager.lite.repository.AccountRepository;
import com.financialmanager.lite.repository.CreditCardRepository;
import com.financialmanager.lite.repository.EntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private CreditCardRepository creditCardRepository;
    @Mock private EntryRepository entryRepository;

    @InjectMocks private DashboardService dashboardService;

    private User user;
    private LocalDate from;
    private LocalDate to;

    @BeforeEach
    void setUp() {
        user = new User();
        YearMonth current = YearMonth.now();
        from = current.atDay(1);
        to = current.atEndOfMonth();
    }

    // Stub padrão de "tudo zerado" — cada teste sobrescreve só o que precisa verificar.
    // Usa lenient() porque nem todo teste exercita ou sobrescreve todos os stubs.
    private void stubAllZero() {
        lenient().when(accountRepository.sumBalanceByUser(user)).thenReturn(BigDecimal.ZERO);
        lenient().when(entryRepository.sumSimpleByUserAndTypeAndDateRange(eq(user), eq("INCOME"), eq(from), eq(to)))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(entryRepository.sumSimpleByUserAndTypeAndDateRange(eq(user), eq("EXPENSE"), eq(from), eq(to)))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(entryRepository.sumRemainingPayablesByUser(user)).thenReturn(BigDecimal.ZERO);
        lenient().when(entryRepository.sumRemainingReceivablesByUser(user)).thenReturn(BigDecimal.ZERO);
        lenient().when(creditCardRepository.findByUserAndActiveTrue(user)).thenReturn(List.of());
        lenient().when(entryRepository.sumExpensesByCategory(eq(user), eq(from), eq(to))).thenReturn(List.of());
        lenient().when(entryRepository.findActivePayablesByUser(user)).thenReturn(List.of());
        lenient().when(entryRepository.findActiveReceivablesByUser(user)).thenReturn(List.of());
    }

    private CreditCard cardWithUsedAmount(BigDecimal usedAmount) {
        return CreditCard.builder()
                .user(user).name("Cartão").creditLimit(new BigDecimal("5000"))
                .usedAmount(usedAmount).closingDay(10).dueDay(20).build();
    }

    @Nested
    @DisplayName("getSummary")
    class GetSummary {

        @Test
        @DisplayName("monta o mapa completo com todos os valores agregados")
        void buildsFullSummaryMap() {
            stubAllZero();
            when(accountRepository.sumBalanceByUser(user)).thenReturn(new BigDecimal("5000.00"));
            when(entryRepository.sumSimpleByUserAndTypeAndDateRange(eq(user), eq("INCOME"), eq(from), eq(to)))
                    .thenReturn(new BigDecimal("1000.00"));
            when(entryRepository.sumSimpleByUserAndTypeAndDateRange(eq(user), eq("EXPENSE"), eq(from), eq(to)))
                    .thenReturn(new BigDecimal("700.00"));
            when(entryRepository.sumRemainingPayablesByUser(user)).thenReturn(new BigDecimal("300.00"));
            when(entryRepository.sumRemainingReceivablesByUser(user)).thenReturn(new BigDecimal("150.00"));
            when(creditCardRepository.findByUserAndActiveTrue(user))
                    .thenReturn(List.of(cardWithUsedAmount(new BigDecimal("400.00"))));
            when(entryRepository.findActivePayablesByUser(user)).thenReturn(List.of(new Entry(), new Entry()));
            when(entryRepository.findActiveReceivablesByUser(user)).thenReturn(List.of(new Entry()));

            Map<String, Object> summary = dashboardService.getSummary(user);

            assertThat(summary.get("totalBalance")).isEqualTo(new BigDecimal("5000.00"));
            assertThat(summary.get("totalCardUsed")).isEqualTo(new BigDecimal("400.00"));
            assertThat((BigDecimal) summary.get("effectiveBalance")).isEqualByComparingTo("4600.00"); // 5000-400
            assertThat(summary.get("totalReceivable")).isEqualTo(new BigDecimal("150.00"));
            assertThat(summary.get("monthIncome")).isEqualTo(new BigDecimal("1000.00"));
            assertThat(summary.get("monthExpenses")).isEqualTo(new BigDecimal("700.00"));
            assertThat((BigDecimal) summary.get("savings")).isEqualByComparingTo("300.00"); // 1000-700
            // 300/1000 * 100 = 30.0% — usa String.format sem Locale, igual ao service (sensível à JVM)
            assertThat(summary.get("savingsRate")).isEqualTo(String.format("%.1f%%", 30.0));
            assertThat(summary.get("totalDebt")).isEqualTo(new BigDecimal("300.00"));
            assertThat(summary.get("activeDebts")).isEqualTo(2L);
            assertThat(summary.get("activeReceivables")).isEqualTo(1L);
            assertThat(summary.get("activeCards")).isEqualTo(1L);
            assertThat(summary.get("period")).isEqualTo(YearMonth.now().toString());
        }

        @Test
        @DisplayName("savingsRate = 0.0% quando não há renda no mês (evita divisão por zero)")
        void savingsRateIsZeroWhenNoIncome() {
            stubAllZero();

            Map<String, Object> summary = dashboardService.getSummary(user);

            assertThat(summary.get("savingsRate")).isEqualTo(String.format("%.1f%%", 0.0));
            assertThat((BigDecimal) summary.get("savings")).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("totalCardUsed soma usedAmount de múltiplos cartões ativos")
        void sumsUsedAmountAcrossMultipleCards() {
            stubAllZero();
            when(creditCardRepository.findByUserAndActiveTrue(user)).thenReturn(List.of(
                    cardWithUsedAmount(new BigDecimal("100.00")),
                    cardWithUsedAmount(new BigDecimal("250.00"))
            ));

            Map<String, Object> summary = dashboardService.getSummary(user);

            assertThat((BigDecimal) summary.get("totalCardUsed")).isEqualByComparingTo("350.00");
            assertThat(summary.get("activeCards")).isEqualTo(2L);
        }

        @Test
        @DisplayName("expenseByCategory converte linhas Object[] em mapa categoria→valor")
        void buildsExpenseByCategoryMap() {
            stubAllZero();
            when(entryRepository.sumExpensesByCategory(eq(user), eq(from), eq(to))).thenReturn(List.of(
                    new Object[]{"Alimentação", new BigDecimal("300.00")},
                    new Object[]{"Transporte", new BigDecimal("150.00")}
            ));

            Map<String, Object> summary = dashboardService.getSummary(user);

            @SuppressWarnings("unchecked")
            Map<String, BigDecimal> byCategory = (Map<String, BigDecimal>) summary.get("expenseByCategory");
            assertThat(byCategory)
                    .containsEntry("Alimentação", new BigDecimal("300.00"))
                    .containsEntry("Transporte", new BigDecimal("150.00"));
        }

        @Test
        @DisplayName("usa o primeiro e o último dia do mês corrente como intervalo de datas")
        void usesCurrentMonthDateRange() {
            stubAllZero();

            dashboardService.getSummary(user);

            YearMonth current = YearMonth.now();
            assertThat(from).isEqualTo(current.atDay(1));
            assertThat(to).isEqualTo(current.atEndOfMonth());
        }
    }
}
