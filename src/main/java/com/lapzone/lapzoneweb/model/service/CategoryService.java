package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.Category;
import com.lapzone.lapzoneweb.model.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    // 1. Lấy tất cả danh mục
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    // 2. Lấy danh mục theo ID
    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục!"));
    }

    // 3. Thêm mới hoặc Cập nhật
    public Category saveCategory(Category category) {
        return categoryRepository.save(category);
    }

    // 4. Xóa danh mục
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }
}