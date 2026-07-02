package com.financialmanager.lite.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "card_numbers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CardNumber extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_card_id", nullable = false)
    private CreditCard creditCard;

    @Column(name = "last_four_digits", nullable = false, length = 4)
    private String lastFourDigits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardKind type;

    public enum CardKind {
        DEBITO, CREDITO, VIRTUAL, FISICO
    }
}
