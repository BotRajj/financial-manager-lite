package com.financialmanager.lite.repository;

import com.financialmanager.lite.model.User;
import com.financialmanager.lite.model.UserCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCategoryRepository extends JpaRepository<UserCategory, Long> {

    List<UserCategory> findByUserOrderByNameAsc(User user);

    Optional<UserCategory> findByUserAndNameIgnoreCase(User user, String name);
}
