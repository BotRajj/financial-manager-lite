package com.financialmanager.lite.service;

import com.financialmanager.lite.model.Budget;
import com.financialmanager.lite.model.User;
import com.financialmanager.lite.repository.BudgetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @InjectMocks
    private BudgetService budgetService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
    }

    // -------------------------------------------------------------------------
    // findByUserAndPeriod
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findByUserAndPeriod")
    class FindByUserAndPeriod {

        @Test
        @DisplayName("retorna lista do repositório para o período dado")
        void returnsRepositoryList() {
            Budget b = Budget.builder()
                    .user(user).category("Alimentação").period("2026-06").limitAmount(new BigDecimal("500")).build();
            when(budgetRepository.findByUserAndPeriodOrderByCategoryAsc(user, "2026-06"))
                    .thenReturn(List.of(b));

            List<Budget> result = budgetService.findByUserAndPeriod(user, "2026-06");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCategory()).isEqualTo("Alimentação");
        }

        @Test
        @DisplayName("retorna lista vazia quando não há orçamentos no período")
        void returnsEmptyList() {
            when(budgetRepository.findByUserAndPeriodOrderByCategoryAsc(user, "2026-06"))
                    .thenReturn(List.of());

            List<Budget> result = budgetService.findByUserAndPeriod(user, "2026-06");

            assertThat(result).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // existsByUserAndCategoryAndPeriod
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("existsByUserAndCategoryAndPeriod")
    class ExistsByUserAndCategoryAndPeriod {

        @Test
        @DisplayName("retorna true quando orçamento existe")
        void returnsTrueWhenExists() {
            Budget b = Budget.builder()
                    .user(user).category("Lazer").period("2026-06").limitAmount(BigDecimal.TEN).build();
            when(budgetRepository.findByUserAndCategoryAndPeriod(user, "Lazer", "2026-06"))
                    .thenReturn(Optional.of(b));

            assertThat(budgetService.existsByUserAndCategoryAndPeriod(user, "Lazer", "2026-06")).isTrue();
        }

        @Test
        @DisplayName("retorna false quando orçamento não existe")
        void returnsFalseWhenAbsent() {
            when(budgetRepository.findByUserAndCategoryAndPeriod(user, "Lazer", "2026-06"))
                    .thenReturn(Optional.empty());

            assertThat(budgetService.existsByUserAndCategoryAndPeriod(user, "Lazer", "2026-06")).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("salva um Budget com os dados corretos")
        void savesCorrectBudget() {
            budgetService.create(user, "Saúde", "2026-06", new BigDecimal("300"));

            verify(budgetRepository).save(argThat(budget ->
                    budget.getUser().equals(user)
                    && "Saúde".equals(budget.getCategory())
                    && "2026-06".equals(budget.getPeriod())
                    && new BigDecimal("300").equals(budget.getLimitAmount())
                    && BigDecimal.ZERO.equals(budget.getSpentAmount())
            ));
        }

        @Test
        @DisplayName("chama save exatamente uma vez")
        void callsSaveOnce() {
            budgetService.create(user, "Transporte", "2026-06", BigDecimal.TEN);

            verify(budgetRepository, times(1)).save(any(Budget.class));
        }
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deleta o orçamento quando ele existe e pertence ao usuário")
        void deletesWhenFound() {
            Budget b = Budget.builder()
                    .user(user).category("Lazer").period("2026-06").limitAmount(BigDecimal.TEN).build();
            when(budgetRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(b));

            budgetService.delete(1L, user);

            verify(budgetRepository).delete(b);
        }

        @Test
        @DisplayName("não chama delete quando orçamento não pertence ao usuário")
        void doesNotDeleteWhenNotFound() {
            when(budgetRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

            budgetService.delete(99L, user);

            verify(budgetRepository, never()).delete(any(Budget.class));
        }
    }

    // -------------------------------------------------------------------------
    // adjustSpent
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("adjustSpent")
    class AdjustSpent {

        private Budget buildBudget() {
            return Budget.builder()
                    .user(user).category("Alimentação").period("2026-06")
                    .limitAmount(new BigDecimal("500"))
                    .spentAmount(new BigDecimal("100"))
                    .build();
        }

        @Test
        @DisplayName("soma o delta positivo ao spentAmount")
        void addsDeltaToSpent() {
            Budget budget = buildBudget();
            when(budgetRepository.findByUserAndCategoryAndPeriod(user, "Alimentação", "2026-06"))
                    .thenReturn(Optional.of(budget));

            budgetService.adjustSpent(user, "Alimentação", "2026-06", new BigDecimal("50"));

            assertThat(budget.getSpentAmount()).isEqualByComparingTo("150");
            verify(budgetRepository).save(budget);
        }

        @Test
        @DisplayName("subtrai o delta negativo do spentAmount")
        void subtractsDeltaFromSpent() {
            Budget budget = buildBudget();
            when(budgetRepository.findByUserAndCategoryAndPeriod(user, "Alimentação", "2026-06"))
                    .thenReturn(Optional.of(budget));

            budgetService.adjustSpent(user, "Alimentação", "2026-06", new BigDecimal("-30"));

            assertThat(budget.getSpentAmount()).isEqualByComparingTo("70");
            verify(budgetRepository).save(budget);
        }

        @Test
        @DisplayName("não permite spentAmount negativo — clamp em zero")
        void clampsSpentAtZero() {
            Budget budget = buildBudget();
            when(budgetRepository.findByUserAndCategoryAndPeriod(user, "Alimentação", "2026-06"))
                    .thenReturn(Optional.of(budget));

            budgetService.adjustSpent(user, "Alimentação", "2026-06", new BigDecimal("-999"));

            assertThat(budget.getSpentAmount()).isEqualByComparingTo("0");
            verify(budgetRepository).save(budget);
        }

        @Test
        @DisplayName("não faz nada quando não existe orçamento para a categoria/período")
        void doesNothingWhenBudgetAbsent() {
            when(budgetRepository.findByUserAndCategoryAndPeriod(user, "Outros", "2026-06"))
                    .thenReturn(Optional.empty());

            budgetService.adjustSpent(user, "Outros", "2026-06", new BigDecimal("50"));

            verify(budgetRepository, never()).save(any(Budget.class));
        }
    }
}
