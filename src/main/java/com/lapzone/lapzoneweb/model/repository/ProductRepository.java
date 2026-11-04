package com.lapzone.lapzoneweb.model.repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import com.lapzone.lapzoneweb.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    @EntityGraph(attributePaths = {"category", "productDetail"})
    @Query("SELECT p FROM Product p WHERE p.stock > 0")
    List<Product> findAvailableProductsForAi();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.stock > 0")
    long countAvailableProductsForAi();

    @EntityGraph(attributePaths = {"category", "productDetail"})
    @Query("""
        SELECT p FROM Product p LEFT JOIN p.productDetail pd
        WHERE p.stock > 0
        AND (:keyword IS NULL
            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(p.category.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(COALESCE(pd.cpu, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(COALESCE(pd.ram, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(COALESCE(pd.storage, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(COALESCE(pd.gpu, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(COALESCE(pd.moreInfo, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY p.price ASC, p.id ASC
        """)
    List<Product> findTopAvailableProductsForAi(
        @Param("keyword") String keyword,
        Pageable pageable
    );

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :qty WHERE p.id = :id AND p.stock >= :qty")
    int decrementStockIfAvailable(@Param("id") Long id, @Param("qty") Integer qty);

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :qty WHERE p.id = :id")
    int incrementStock(@Param("id") Long id, @Param("qty") Integer qty);

    List<Product> findTop8ByOrderByIdDesc();
    List<Product> findTop4ByOrderByIdDesc();
    List<Product> findByCategory_Name(String categoryName);
   @Query("SELECT p FROM Product p LEFT JOIN p.productDetail pd WHERE " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.category.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(pd.moreInfo) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
           "AND (:minPrice IS NULL OR p.price >= :minPrice) " +
           "AND (:maxPrice IS NULL OR p.price <= :maxPrice) " +
           "AND (:cpu IS NULL OR pd.cpu LIKE %:cpu%) " +
           "AND (:gpu IS NULL OR pd.gpu LIKE %:gpu%) " +
           "AND (:ram IS NULL OR pd.ram LIKE %:ram%) " +
           "AND (:storage IS NULL OR pd.storage LIKE %:storage%) " +
           "AND (:screen IS NULL OR pd.screen LIKE %:screen%)")
    Page<Product> searchAndFilterProducts(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("cpu") String cpu,
            @Param("gpu") String gpu,
            @Param("ram") String ram,
            @Param("storage") String storage,
            @Param("screen") String screen,
            org.springframework.data.domain.Pageable pageable
    );
    List<Product> findByCategoryId(Long categoryId);
    List<Product> findTop4ByCategoryIdAndIdNot(Long categoryId, Long productId);
}