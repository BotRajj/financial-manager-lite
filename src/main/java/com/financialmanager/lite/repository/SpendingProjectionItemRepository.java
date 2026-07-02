package com.financialmanager.lite.repository;

import com.financialmanager.lite.model.SpendingProjectionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpendingProjectionItemRepository extends JpaRepository<SpendingProjectionItem, Long> {

    @Query("SELECT i FROM SpendingProjectionItem i " +
           "LEFT JOIN FETCH i.entry LEFT JOIN FETCH i.card " +
           "WHERE i.projection.id = :projId ORDER BY i.createdAt ASC")
    List<SpendingProjectionItem> findByProjectionIdWithDetails(@Param("projId") Long projId);

    @Query("SELECT i FROM SpendingProjectionItem i WHERE i.id = :id AND i.projection.user.id = :userId")
    Optional<SpendingProjectionItem> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
