package com.example.demo.controller;

import com.example.demo.model.DTO.CategoryDTO;
import com.example.demo.service.CategoryService;

import java.util.*;

public class PageResourceController {
    /*
    * 카테고리 목록 등 메인페이지 관련 요소들을 관리하는 컨트롤러
    */
    CategoryService categoryService;
    public List<CategoryDTO> getAllCategories() {
        return categoryService.getAllCategories();
    }
}
