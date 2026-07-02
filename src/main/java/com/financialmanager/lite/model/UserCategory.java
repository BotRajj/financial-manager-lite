package com.financialmanager.lite.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_categories",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserCategory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String name;
}
