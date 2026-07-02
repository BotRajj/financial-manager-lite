package com.financialmanager.lite.repository;

import com.financialmanager.lite.model.Entry;
import com.financialmanager.lite.model.EntryInstallment;
import com.financialmanager.lite.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EntryInstallmentRepository extends JpaRepository<EntryInstallment, Long> {

    List<EntryInstallment> findByEntryOrderByInstallmentNumberAsc(Entry entry);

    Optional<EntryInstallment> findByEntryAndInstallmentNumber(Entry entry, Integer installmentNumber);

    boolean existsByEntry(Entry entry);

    @Query("SELECT i FROM EntryInstallment i " +
           "LEFT JOIN FETCH i.entry e " +
           "LEFT JOIN FETCH e.account " +
           "LEFT JOIN FETCH e.creditCard " +
           "WHERE i.id = :id AND e.user = :user")
    Optional<EntryInstallment> findByIdAndUser(@Param("id") Long id, @Param("user") User user);

    @Query("SELECT i FROM EntryInstallment i " +
           "LEFT JOIN FETCH i.entry e " +
           "LEFT JOIN FETCH e.account " +
           "LEFT JOIN FETCH e.creditCard " +
           "WHERE e.user = :user AND i.dueDate BETWEEN :from AND :to " +
           "AND i.confirmed = false AND e.status = 'ACTIVE' " +
           "ORDER BY i.dueDate ASC")
    List<EntryInstallment> findUnconfirmedDueInRange(
            @Param("user") User user, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT i FROM EntryInstallment i " +
           "LEFT JOIN FETCH i.entry e " +
           "LEFT JOIN FETCH e.account " +
           "LEFT JOIN FETCH e.creditCard " +
           "WHERE e.user = :user AND i.dueDate > :from " +
           "AND i.confirmed = false AND e.status = 'ACTIVE' " +
           "ORDER BY i.dueDate ASC")
    List<EntryInstallment> findUnconfirmedDueAfter(
            @Param("user") User user, @Param("from") LocalDate from);
}
