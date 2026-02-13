package com.example.demo.service;

import com.example.demo.entity.Category;
import com.example.demo.model.DTO.CategoryDTO;
import com.example.demo.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryService {
    @Autowired
    CategoryRepository categoryRepository;

    public Optional<CategoryDTO> getCategoryById(Long id) {
        Optional<Category> oneCategory = categoryRepository.findById(id);
        return oneCategory.map(category -> new CategoryDTO(
                category.getCategoryId(),
                category.getCategoryName()
        ));
    }

    public List<CategoryDTO> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(category -> new CategoryDTO(
                        category.getCategoryId(),
                        category.getCategoryName()
                ))
                .toList();
    }

    public Category saveCategory(Category category) {
        return categoryRepository.save(category);
    }

    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }
}
