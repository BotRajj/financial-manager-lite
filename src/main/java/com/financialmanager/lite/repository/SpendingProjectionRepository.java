package com.financialmanager.lite.repository;

import com.financialmanager.lite.model.SpendingProjection;
import com.financialmanager.lite.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpendingProjectionRepository extends JpaRepository<SpendingProjection, Long> {
    @Query("SELECT DISTINCT p FROM SpendingProjection p LEFT JOIN FETCH p.items WHERE p.user = :user ORDER BY p.createdAt DESC")
    List<SpendingProjection> findByUserOrderByCreatedAtDesc(@Param("user") User user);
    Optional<SpendingProjection> findByIdAndUser(Long id, User user);
}
