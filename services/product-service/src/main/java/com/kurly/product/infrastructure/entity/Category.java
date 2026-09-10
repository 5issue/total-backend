package com.kurly.product.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "name", length = 225, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20)
    private CategoryType type;

    @Column(name = "sequence")
    private Integer sequence;

    @Builder
    private Category(Long parentId, String name, CategoryType type, Integer sequence) {
        this.parentId = parentId;
        this.name = name;
        this.type = type;
        this.sequence = sequence;
    }

    public enum CategoryType {
        STANDARD,
        DISPLAY
    }
}
