package com.financialmanager.lite.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "spending_projections")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpendingProjection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    /** INCLUDE = só itens selecionados | EXCLUDE_FROM_ALL = todos menos os excluídos */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String mode = "INCLUDE";

    @OneToMany(mappedBy = "projection", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SpendingProjectionItem> items = new ArrayList<>();
}
