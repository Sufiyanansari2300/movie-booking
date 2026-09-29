package com.sufiyan.moviebooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "refund_policies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundPolicy extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active;

    public RefundPolicy(String name) {
        this.name = name;
    }
}
