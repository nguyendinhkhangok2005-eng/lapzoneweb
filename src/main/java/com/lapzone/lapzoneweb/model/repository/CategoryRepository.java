package com.lapzone.lapzoneweb.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.lapzone.lapzoneweb.model.entity.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
}