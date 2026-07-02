package com.financialmanager.lite.repository;

import com.financialmanager.lite.model.Account;
import com.financialmanager.lite.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.bank WHERE a.user = :user AND a.active = true ORDER BY a.name")
    List<Account> findByUserAndActiveTrue(@Param("user") User user);

    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.bank WHERE a.user = :user AND a.active = false ORDER BY a.name")
    List<Account> findByUserAndActiveFalse(@Param("user") User user);

    Optional<Account> findByIdAndUser(Long id, User user);

    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.bank WHERE a.id = :id AND a.user = :user")
    Optional<Account> findByIdAndUserWithBank(@Param("id") Long id, @Param("user") User user);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.user = :user AND a.active = true")
    BigDecimal sumBalanceByUser(@Param("user") User user);
}

