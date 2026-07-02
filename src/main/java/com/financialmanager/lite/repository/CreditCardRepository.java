package com.financialmanager.lite.repository;

import com.financialmanager.lite.model.CreditCard;
import com.financialmanager.lite.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CreditCardRepository extends JpaRepository<CreditCard, Long> {

    @Query("SELECT c FROM CreditCard c LEFT JOIN FETCH c.bank LEFT JOIN FETCH c.cardNumbers WHERE c.user = :user AND c.active = true ORDER BY c.name")
    List<CreditCard> findByUserAndActiveTrue(@Param("user") User user);

    @Query("SELECT c FROM CreditCard c LEFT JOIN FETCH c.bank LEFT JOIN FETCH c.cardNumbers WHERE c.user = :user AND c.active = false ORDER BY c.name")
    List<CreditCard> findByUserAndActiveFalse(@Param("user") User user);

    Optional<CreditCard> findByIdAndUser(Long id, User user);

    @Query("SELECT c FROM CreditCard c LEFT JOIN FETCH c.bank WHERE c.id = :id AND c.user = :user")
    Optional<CreditCard> findByIdAndUserWithBank(@Param("id") Long id, @Param("user") User user);

    @Query("SELECT c FROM CreditCard c LEFT JOIN FETCH c.cardNumbers WHERE c.id = :id AND c.user = :user")
    Optional<CreditCard> findByIdAndUserWithCardNumbers(@Param("id") Long id, @Param("user") User user);
}
