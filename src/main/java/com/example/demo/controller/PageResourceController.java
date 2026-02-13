package com.example.demo.controller;

import com.example.demo.model.DTO.CategoryDTO;
import com.example.demo.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
@RestController
public class PageResourceController {
    /*
    * 카테고리 목록 등 메인페이지 관련 요소들을 관리하는 컨트롤러
    */
    @Autowired
    private final CategoryService categoryService;

    public PageResourceController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/categories")
    public List<CategoryDTO> getAllCategories() {
        return categoryService.getAllCategories();
    }
}
