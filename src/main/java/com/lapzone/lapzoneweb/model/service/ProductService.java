package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public List<Product> getNewProducts() {
        return productRepository.findTop8ByOrderByIdDesc();
    }

    public List<Product> getReferenceProducts() {
        return productRepository.findTop4ByOrderByIdDesc();
    }

    public List<Product> getProductsByBrand(String brand) {
        return productRepository.findByCategory_Name(brand);
    }

    public List<Product> getRelatedProducts(Long categoryId, Long productId) {
        return productRepository.findTop4ByCategoryIdAndIdNot(categoryId, productId);
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id).orElse(null);
    }

   public org.springframework.data.domain.Page<Product> searchProducts(
           String keyword, Long categoryId, Double minPrice, Double maxPrice, 
           String cpu, String gpu, String ram, String storage, String screen, 
           org.springframework.data.domain.Pageable pageable) {
        String kw = (keyword != null) ? keyword : ""; 
        String cpuFilter = (cpu != null && !cpu.trim().isEmpty()) ? cpu : null;
        String gpuFilter = (gpu != null && !gpu.trim().isEmpty()) ? gpu : null;
        String ramFilter = (ram != null && !ram.trim().isEmpty()) ? ram : null;
        String storageFilter = (storage != null && !storage.trim().isEmpty()) ? storage : null;
        String screenFilter = (screen != null && !screen.trim().isEmpty()) ? screen : null;
        
        return productRepository.searchAndFilterProducts(kw, categoryId, minPrice, maxPrice, cpuFilter, gpuFilter, ramFilter, storageFilter, screenFilter, pageable);
    }
    @Transactional
    public void saveProduct(Product product) {
        productRepository.save(product);
    }

   
    @Transactional
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }
    public List<Product> getProductsByCategoryId(Long categoryId) {
        return productRepository.findByCategoryId(categoryId);
    }
}