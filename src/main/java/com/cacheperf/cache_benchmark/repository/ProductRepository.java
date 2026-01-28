package com.cacheperf.cache_benchmark.repository;

import com.cacheperf.cache_benchmark.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    List<Product> findByCategory(String category);

    List<Product> findByBrand(String brand);

    List<Product> findByActiveTrue();

    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.active = true")
    List<Product> findActiveByCategoryProducts(@Param("category") String category);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.active = true")
    long countActiveProducts();

    boolean existsBySku(String sku);
}
