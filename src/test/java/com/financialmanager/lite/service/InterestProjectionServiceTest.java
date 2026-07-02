package com.financialmanager.lite.service;

import com.financialmanager.lite.model.Entry;
import com.financialmanager.lite.model.EntryInstallment;
import com.financialmanager.lite.model.User;
import com.financialmanager.lite.service.InterestProjectionService.InstallmentFeeResult;
import com.financialmanager.lite.service.InterestProjectionService.ProjectionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// Nenhum mock necessário — o service é lógica pura sem dependências injetadas
class InterestProjectionServiceTest {

    private final InterestProjectionService service = new InterestProjectionService();

    // Auxiliares para construir objetos sem repetição
    private Entry entryWith(BigDecimal amount, BigDecimal lateFeeRate, BigDecimal interestRate) {
        return Entry.builder()
                .user(new User()).description("Test").type("EXPENSE")
                .amount(amount).paidAmount(BigDecimal.ZERO)
                .lateFeeRate(lateFeeRate).interestRate(interestRate)
                .build();
    }

    private EntryInstallment instWith(BigDecimal amount, LocalDate dueDate, boolean confirmed) {
        return EntryInstallment.builder()
                .amount(amount).dueDate(dueDate).confirmed(confirmed).build();
    }

    // =========================================================================
    // calculateInstallmentFee
    // =========================================================================

    @Nested
    @DisplayName("calculateInstallmentFee")
    class CalculateInstallmentFee {

        @Test
        @DisplayName("parcela já confirmada → retorno imediato sem taxas")
        void confirmedInstallmentReturnsNoFees() {
            EntryInstallment inst = instWith(new BigDecimal("500.00"),
                    LocalDate.now().minusDays(30), true); // confirmed=true
            Entry entry = entryWith(new BigDecimal("500"), new BigDecimal("0.02"), new BigDecimal("0.15"));

            InstallmentFeeResult result = service.calculateInstallmentFee(inst, entry);

            assertThat(result.daysLate()).isZero();
            assertThat(result.lateFeeAmount()).isEqualByComparingTo("0.00");
            assertThat(result.interestAmount()).isEqualByComparingTo("0.00");
            assertThat(result.totalDue()).isEqualByComparingTo("500.00");
            assertThat(result.isOverdue()).isFalse();
            assertThat(result.hasFees()).isFalse();
        }

        @Test
        @DisplayName("dueDate nulo → retorno imediato sem taxas")
        void nullDueDateReturnsNoFees() {
            EntryInstallment inst = instWith(new BigDecimal("300.00"), null, false);
            Entry entry = entryWith(new BigDecimal("300"), new BigDecimal("0.02"), new BigDecimal("0.15"));

            InstallmentFeeResult result = service.calculateInstallmentFee(inst, entry);

            assertThat(result.daysLate()).isZero();
            assertThat(result.totalDue()).isEqualByComparingTo("300.00");
            assertThat(result.hasFees()).isFalse();
        }

        @Test
        @DisplayName("vencimento futuro → daysLate=0, sem taxas")
        void futureDueDateReturnsNoFees() {
            EntryInstallment inst = instWith(new BigDecimal("1000.00"),
                    LocalDate.now().plusDays(10), false);
            Entry entry = entryWith(new BigDecimal("1000"), new BigDecimal("0.02"), new BigDecimal("0.15"));

            InstallmentFeeResult result = service.calculateInstallmentFee(inst, entry);

            assertThat(result.daysLate()).isZero();
            assertThat(result.totalDue()).isEqualByComparingTo("1000.00");
            assertThat(result.isOverdue()).isFalse();
        }

        @Test
        @DisplayName("em atraso mas ambas as taxas nulas → daysLate>0, totalDue=original")
        void lateWithNullRatesAppliesNoFees() {
            EntryInstallment inst = instWith(new BigDecimal("1000.00"),
                    LocalDate.now().minusDays(30), false);
            Entry entry = entryWith(new BigDecimal("1000"), null, null);

            InstallmentFeeResult result = service.calculateInstallmentFee(inst, entry);

            assertThat(result.daysLate()).isEqualTo(30);
            assertThat(result.lateFeeAmount()).isEqualByComparingTo("0.00");
            assertThat(result.interestAmount()).isEqualByComparingTo("0.00");
            assertThat(result.totalDue()).isEqualByComparingTo("1000.00");
            assertThat(result.isOverdue()).isTrue();
            assertThat(result.hasFees()).isFalse();
        }

        @Test
        @DisplayName("30 dias em atraso, só multa (sem juros) → lateFee aplicada, sem juros compostos")
        void lateWithOnlyLateFeeAppliesNoCompoundInterest() {
            // interestRate = null → monthlyRate = 0 → dailyRate = 0 → sem juros compostos
            EntryInstallment inst = instWith(new BigDecimal("1000.00"),
                    LocalDate.now().minusDays(30), false);
            Entry entry = entryWith(new BigDecimal("1000"), new BigDecimal("0.0200"), null);

            InstallmentFeeResult result = service.calculateInstallmentFee(inst, entry);

            assertThat(result.daysLate()).isEqualTo(30);
            assertThat(result.originalAmount()).isEqualByComparingTo("1000.00");
            assertThat(result.lateFeeAmount()).isEqualByComparingTo("20.00");   // 1000 * 2%
            assertThat(result.interestAmount()).isEqualByComparingTo("0.00");   // sem juros diários
            assertThat(result.totalDue()).isEqualByComparingTo("1020.00");      // base = 1000 + 20
            assertThat(result.hasFees()).isTrue();
        }

        @Test
        @DisplayName("30 dias em atraso, multa 2% + juros 15% a.m. → total ≈ R$ 1173")
        void lateWithBothRatesAppliesLateFeeAndCompoundInterest() {
            // Exatamente 30 dias em atraso garante: base * (1+dailyRate)^30 = base * 1.15
            // lateFee = 1000 * 0.02 = 20 → base = 1020 → total = 1020 * 1.15 = 1173.00
            EntryInstallment inst = instWith(new BigDecimal("1000.00"),
                    LocalDate.now().minusDays(30), false);
            Entry entry = entryWith(new BigDecimal("1000"),
                    new BigDecimal("0.0200"), new BigDecimal("0.1500"));

            InstallmentFeeResult result = service.calculateInstallmentFee(inst, entry);

            assertThat(result.daysLate()).isEqualTo(30);
            assertThat(result.originalAmount()).isEqualByComparingTo("1000.00");
            assertThat(result.lateFeeAmount()).isEqualByComparingTo("20.00");
            // juros compostos: base(1020) × 1.15 — tolerância de R$ 0.02 por precisão double
            assertThat(result.totalDue())
                    .isCloseTo(new BigDecimal("1173.00"), within(new BigDecimal("0.02")));
            // interest = total - base ≈ 1173 - 1020 = 153
            assertThat(result.interestAmount())
                    .isCloseTo(new BigDecimal("153.00"), within(new BigDecimal("0.02")));
            assertThat(result.isOverdue()).isTrue();
            assertThat(result.hasFees()).isTrue();
        }
    }

    // =========================================================================
    // project
    // =========================================================================

    @Nested
    @DisplayName("project")
    class Project {

        @Test
        @DisplayName("taxas zero → todos os valores da projeção iguais ao principal")
        void zeroRatesFlatProjection() {
            Entry entry = Entry.builder()
                    .user(new User()).description("Test").type("EXPENSE")
                    .amount(new BigDecimal("2000")).paidAmount(new BigDecimal("500"))
                    .lateFeeRate(BigDecimal.ZERO).interestRate(BigDecimal.ZERO)
                    .defaultSinceDate(LocalDate.now())
                    .build();

            ProjectionResult result = service.project(entry, 1);

            // principal = 2000 - 500 = 1500; base = 1500; daily = 0 → todos os dias = 1500
            assertThat(result.principal()).isEqualByComparingTo("1500.00");
            assertThat(result.lateFeeAmount()).isEqualByComparingTo("0.00");
            assertThat(result.projection().values())
                    .allSatisfy(v -> assertThat(v).isEqualByComparingTo("1500.00"));
        }

        @Test
        @DisplayName("só multa, sem juros → lateFeeAmount correto e projeção plana em base")
        void onlyLateFeeFlatsProjectionAtBase() {
            Entry entry = Entry.builder()
                    .user(new User()).description("Test").type("EXPENSE")
                    .amount(new BigDecimal("1000")).paidAmount(BigDecimal.ZERO)
                    .lateFeeRate(new BigDecimal("0.0500")).interestRate(BigDecimal.ZERO)
                    .defaultSinceDate(LocalDate.now())
                    .build();

            ProjectionResult result = service.project(entry, 1);

            assertThat(result.lateFeeAmount()).isEqualByComparingTo("50.00");    // 1000 * 5%
            assertThat(result.baseAmount()).isEqualByComparingTo("1050.00");     // 1000 + 50
            // dailyRate = 0 → todos os dias = 1050
            assertThat(result.projection().values())
                    .allSatisfy(v -> assertThat(v).isEqualByComparingTo("1050.00"));
        }

        @Test
        @DisplayName("dia 0 (since) sempre igual à base, independente da taxa")
        void dayZeroAlwaysEqualsBase() {
            LocalDate since = LocalDate.now();
            Entry entry = Entry.builder()
                    .user(new User()).description("Test").type("EXPENSE")
                    .amount(new BigDecimal("1000")).paidAmount(BigDecimal.ZERO)
                    .lateFeeRate(new BigDecimal("0.0200")).interestRate(new BigDecimal("0.1500"))
                    .defaultSinceDate(since)
                    .build();

            ProjectionResult result = service.project(entry, 1);

            // dia 0: (1+dailyRate)^0 = 1 → total = base
            assertThat(result.projection().get(since)).isEqualByComparingTo(result.baseAmount());
        }

        @Test
        @DisplayName("dia 30 com taxa 15% a.m. ≈ base × 1.15")
        void day30ApproximatesOneMonthGrowth() {
            LocalDate since = LocalDate.now();
            Entry entry = Entry.builder()
                    .user(new User()).description("Test").type("EXPENSE")
                    .amount(new BigDecimal("1000")).paidAmount(BigDecimal.ZERO)
                    .lateFeeRate(BigDecimal.ZERO).interestRate(new BigDecimal("0.1500"))
                    .defaultSinceDate(since)
                    .build();

            ProjectionResult result = service.project(entry, 2); // 2 meses garante que dia+30 está incluso

            BigDecimal day30Value = result.projection().get(since.plusDays(30));
            // base = 1000; após 30 dias a 15% a.m. → ≈ 1150
            assertThat(day30Value).isCloseTo(new BigDecimal("1150.00"), within(new BigDecimal("0.02")));
        }

        @Test
        @DisplayName("defaultSinceDate é a primeira chave da projeção")
        void defaultSinceDateIsFirstProjectionKey() {
            LocalDate since = LocalDate.now().minusDays(5);
            Entry entry = Entry.builder()
                    .user(new User()).description("Test").type("EXPENSE")
                    .amount(new BigDecimal("500")).paidAmount(BigDecimal.ZERO)
                    .lateFeeRate(BigDecimal.ZERO).interestRate(BigDecimal.ZERO)
                    .defaultSinceDate(since)
                    .build();

            ProjectionResult result = service.project(entry, 1);

            assertThat(result.defaultSinceDate()).isEqualTo(since);
            assertThat(result.projection().keySet().iterator().next()).isEqualTo(since);
        }

        @Test
        @DisplayName("months=0 → projeção termina hoje e contém a data de hoje")
        void monthsZeroEndsToday() {
            LocalDate today = LocalDate.now();
            Entry entry = Entry.builder()
                    .user(new User()).description("Test").type("EXPENSE")
                    .amount(new BigDecimal("800")).paidAmount(BigDecimal.ZERO)
                    .lateFeeRate(BigDecimal.ZERO).interestRate(BigDecimal.ZERO)
                    .defaultSinceDate(today)
                    .build();

            ProjectionResult result = service.project(entry, 0);

            assertThat(result.projection()).containsKey(today);
            assertThat(result.projection()).doesNotContainKey(today.plusDays(1));
        }
    }
}
