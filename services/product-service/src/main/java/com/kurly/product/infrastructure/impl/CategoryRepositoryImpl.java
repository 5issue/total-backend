package com.kurly.product.infrastructure.impl;

import com.kurly.product.domain.repository.CategoryRepository;
import com.kurly.product.infrastructure.entity.Category;
import com.kurly.product.infrastructure.jpa.CategoryJpaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryRepository {

    private final CategoryJpaRepository categoryJpaRepository;


    @Override
    public List<Category> findAll() {
        return categoryJpaRepository.findAll();
    }
}
