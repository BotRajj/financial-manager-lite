package com.financialmanager.lite.repository;

import com.financialmanager.lite.model.Account;
import com.financialmanager.lite.model.CreditCard;
import com.financialmanager.lite.model.Entry;
import com.financialmanager.lite.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EntryRepository extends JpaRepository<Entry, Long> {

    Optional<Entry> findByIdAndUser(Long id, User user);

    // ── SIMPLE (lançamentos) ──────────────────────────────────────────────

    @Query(value = "SELECT e FROM Entry e LEFT JOIN FETCH e.account LEFT JOIN FETCH e.creditCard " +
                   "WHERE e.user = :user AND e.mode = 'SIMPLE' " +
            "ORDER BY e.entryDate DESC",
           countQuery = "SELECT COUNT(e) FROM Entry e " +
                   "WHERE e.user = :user AND e.mode = 'SIMPLE'")
    Page<Entry> findSimpleByUser(@Param("user") User user, Pageable pageable);

    @Query(value = "SELECT e FROM Entry e LEFT JOIN FETCH e.account LEFT JOIN FETCH e.creditCard " +
                   "WHERE e.user = :user AND e.mode = 'SIMPLE' AND e.account.id = :accountId " +
            "ORDER BY e.entryDate DESC",
           countQuery = "SELECT COUNT(e) FROM Entry e " +
                   "WHERE e.user = :user AND e.mode = 'SIMPLE' AND e.account.id = :accountId")
    Page<Entry> findSimpleByUserAndAccount(@Param("user") User user, @Param("accountId") Long accountId, Pageable pageable);

    @Query(value = "SELECT e FROM Entry e LEFT JOIN FETCH e.account LEFT JOIN FETCH e.creditCard " +
                   "WHERE e.user = :user AND e.mode = 'SIMPLE' AND e.creditCard.id = :cardId " +
            "ORDER BY e.entryDate DESC",
           countQuery = "SELECT COUNT(e) FROM Entry e " +
                   "WHERE e.user = :user AND e.mode = 'SIMPLE' AND e.creditCard.id = :cardId")
    Page<Entry> findSimpleByUserAndCreditCard(@Param("user") User user, @Param("cardId") Long cardId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Entry e " +
           "WHERE e.user = :user AND e.mode = 'SIMPLE' AND e.type = :type " +
           "AND e.entryDate BETWEEN :from AND :to")
    BigDecimal sumSimpleByUserAndTypeAndDateRange(
            @Param("user") User user, @Param("type") String type,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT e.category, COALESCE(SUM(e.amount), 0) FROM Entry e " +
           "WHERE e.user = :user " +
            "AND e.mode = 'SIMPLE' " +
            "AND e.type = 'EXPENSE' " +
            "AND e.entryDate BETWEEN :from AND :to " +
            "GROUP BY e.category")
    List<Object[]> sumExpensesByCategory(
            @Param("user") User user, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT e FROM Entry e " +
            "WHERE e.creditCard = :card " +
            "AND e.mode = 'SIMPLE' " +
           "AND ((e.invoiceMonth = :month) OR (e.invoiceMonth IS NULL " +
            "AND e.entryDate BETWEEN :from AND :to)) " +
           "ORDER BY e.entryDate DESC")
    List<Entry> findSimpleByCreditCardAndInvoice(
            @Param("card") CreditCard card, @Param("month") String month,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.type = 'EXPENSE' THEN e.amount ELSE -e.amount END), 0) FROM Entry e " +
           "WHERE e.creditCard = :card AND e.mode = 'SIMPLE' " +
           "AND ((e.invoiceMonth = :month) OR (e.invoiceMonth IS NULL " +
            "AND e.entryDate BETWEEN :from AND :to))")
    BigDecimal sumSimpleByCreditCardAndInvoice(
            @Param("card") CreditCard card, @Param("month") String month,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM Entry e " +
           "WHERE e.creditCard = :card " +
            "AND e.mode = 'SIMPLE' " +
           "AND e.description LIKE :prefix " +
            "AND e.entryDate >= :since")
    boolean existsLateChargeForCard(@Param("card") CreditCard card,
                                    @Param("prefix") String prefix,
                                    @Param("since") LocalDate since);

    @Query("SELECT e FROM Entry e LEFT JOIN FETCH e.account LEFT JOIN FETCH e.creditCard " +
           "WHERE e.user = :user " +
            "AND e.mode = 'SIMPLE' " +
            "AND e.entryDate >= :from " +
            "ORDER BY e.entryDate ASC")
    List<Entry> findFutureSimpleByUser(@Param("user") User user, @Param("from") LocalDate from);

    // ── RECURRING (recorrentes) ───────────────────────────────────────────

    @Query("SELECT e FROM Entry e LEFT JOIN FETCH e.account LEFT JOIN FETCH e.creditCard " +
           "WHERE e.user = :user AND e.mode = 'RECURRING' AND e.active = true " +
           "ORDER BY e.type DESC, e.description ASC")
    List<Entry> findRecurringActiveByUser(@Param("user") User user);

    @Query("SELECT e FROM Entry e LEFT JOIN FETCH e.account LEFT JOIN FETCH e.creditCard LEFT JOIN FETCH e.user " +
           "WHERE e.mode = 'RECURRING' AND e.active = true AND e.autoApply = true")
    List<Entry> findAllAutoApplyRecurring();

    @Query("SELECT e FROM Entry e " +
            "WHERE e.id = :id AND e.user = :user AND e.mode = 'RECURRING'")
    Optional<Entry> findRecurringByIdAndUser(@Param("id") Long id, @Param("user") User user);

    // ── INSTALLMENT (parcelas/dívidas) ────────────────────────────────────

    @Query("SELECT DISTINCT e FROM Entry e LEFT JOIN FETCH e.account LEFT JOIN FETCH e.creditCard " +
           "LEFT JOIN FETCH e.bank LEFT JOIN FETCH e.installments " +
           "WHERE e.user = :user AND e.mode = 'INSTALLMENT' " +
            "ORDER BY e.createdAt DESC")
    List<Entry> findInstallmentsByUser(@Param("user") User user);

    @Query("SELECT e FROM Entry e LEFT JOIN FETCH e.creditCard " +
           "WHERE e.id = :id AND e.user = :user AND e.mode = 'INSTALLMENT'")
    Optional<Entry> findInstallmentByIdAndUser(@Param("id") Long id, @Param("user") User user);

    @Query("SELECT e FROM Entry e LEFT JOIN FETCH e.creditCard cc LEFT JOIN FETCH cc.bank LEFT JOIN FETCH e.bank " +
           "WHERE e.user = :user AND e.mode = 'INSTALLMENT' " +
           "AND e.status = 'ACTIVE' AND e.direction = 'PAYABLE' " +
            "ORDER BY e.dayOfMonth ASC NULLS LAST")
    List<Entry> findActivePayablesByUser(@Param("user") User user);

    @Query("SELECT e FROM Entry e " +
            "WHERE e.user = :user AND e.mode = 'INSTALLMENT' " +
           "AND e.status = 'ACTIVE' AND e.direction = 'RECEIVABLE' " +
            "ORDER BY e.dayOfMonth ASC NULLS LAST")
    List<Entry> findActiveReceivablesByUser(@Param("user") User user);

    @Query("SELECT DISTINCT e FROM Entry e LEFT JOIN FETCH e.installments " +
           "WHERE e.creditCard = :card " +
            "AND e.mode = 'INSTALLMENT' " +
            "AND e.status = 'ACTIVE' " +
            "AND e.direction = 'PAYABLE'")
    List<Entry> findActiveInstallmentsByCreditCard(@Param("card") CreditCard card);

    @Query("SELECT e FROM Entry e " +
            "WHERE e.account = :account AND e.mode = 'INSTALLMENT' " +
           "AND e.status = 'ACTIVE' AND e.direction = 'PAYABLE' " +
            "ORDER BY e.dayOfMonth ASC NULLS LAST")
    List<Entry> findActiveInstallmentsByAccount(@Param("account") Account account);

    @Query("SELECT COALESCE(SUM(e.amount - e.paidAmount), 0) FROM Entry e " +
           "WHERE e.user = :user AND e.mode = 'INSTALLMENT' AND e.status = 'ACTIVE' AND e.direction = 'PAYABLE'")
    BigDecimal sumRemainingPayablesByUser(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(e.amount - e.paidAmount), 0) FROM Entry e " +
           "WHERE e.user = :user AND e.mode = 'INSTALLMENT' AND e.status = 'ACTIVE' AND e.direction = 'RECEIVABLE'")
    BigDecimal sumRemainingReceivablesByUser(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Entry e " +
           "WHERE e.user = :user AND e.account.id = :accountId AND e.mode = 'SIMPLE' AND e.type = :type")
    BigDecimal sumSimpleByAccountAndType(@Param("user") User user,
                                         @Param("accountId") Long accountId,
                                         @Param("type") String type);
}
