package com.financialmanager.lite.repository;

import com.financialmanager.lite.model.Budget;
import com.financialmanager.lite.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUserAndPeriodOrderByCategoryAsc(User user, String period);
    Optional<Budget> findByUserAndCategoryAndPeriod(User user, String category, String period);
    Optional<Budget> findByIdAndUser(Long id, User user);
}
