package com.financialmanager.lite.repository;

import com.financialmanager.lite.model.Bank;
import com.financialmanager.lite.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankRepository extends JpaRepository<Bank, Long> {
    List<Bank> findByUserAndActiveTrueOrderByNameAsc(User user);
    Optional<Bank> findByIdAndUser(Long id, User user);
}
