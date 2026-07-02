package com.financialmanager.lite.service;

import com.financialmanager.lite.model.Budget;
import com.financialmanager.lite.model.User;
import com.financialmanager.lite.repository.BudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;

    public List<Budget> findByUserAndPeriod(User user, String period) {
        return budgetRepository.findByUserAndPeriodOrderByCategoryAsc(user, period);
    }

    public boolean existsByUserAndCategoryAndPeriod(User user, String category, String period) {
        return budgetRepository.findByUserAndCategoryAndPeriod(user, category, period).isPresent();
    }

    @Transactional
    public void create(User user, String category, String period, BigDecimal limitAmount) {
        budgetRepository.save(Budget.builder()
                .user(user).category(category).period(period).limitAmount(limitAmount).build());
    }

    @Transactional
    public void delete(Long id, User user) {
        budgetRepository.findByIdAndUser(id, user).ifPresent(budgetRepository::delete);
    }

    @Transactional
    public void adjustSpent(User user, String category, String period, BigDecimal delta) {
        budgetRepository.findByUserAndCategoryAndPeriod(user, category, period).ifPresent(budget -> {
            BigDecimal newSpent = budget.getSpentAmount().add(delta);
            budget.setSpentAmount(newSpent.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newSpent);
            budgetRepository.save(budget);
        });
    }
}
