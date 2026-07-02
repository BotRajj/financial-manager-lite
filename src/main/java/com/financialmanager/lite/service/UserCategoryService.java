package com.financialmanager.lite.service;

import com.financialmanager.lite.model.User;
import com.financialmanager.lite.model.UserCategory;
import com.financialmanager.lite.repository.UserCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserCategoryService {

    private final UserCategoryRepository repository;

    public List<UserCategory> findAll(User user) {
        return repository.findByUserOrderByNameAsc(user);
    }

    /** Retorna a categoria existente ou cria uma nova se não existir. */
    public String resolveAndSave(User user, String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.trim();
        repository.findByUserAndNameIgnoreCase(user, trimmed)
                .orElseGet(() -> repository.save(
                        UserCategory.builder().user(user).name(trimmed).build()));
        return trimmed;
    }
}
