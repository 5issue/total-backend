package com.kurly.product.domain.repository;


import com.kurly.product.infrastructure.entity.Category;
import java.util.List;

public interface CategoryRepository {
    List<Category> findAll();
}
