package com.financialmanager.lite.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "banks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Bank extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
