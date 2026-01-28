package com.cacheperf.cache_benchmark.service;

import com.cacheperf.cache_benchmark.model.Product;
import com.cacheperf.cache_benchmark.model.dto.ProductDTO;
import com.cacheperf.cache_benchmark.model.dto.ProductRequest;
import com.cacheperf.cache_benchmark.repository.ProductRepository;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Timed(value = "product.find.by.id", description = "Time taken to find product by ID")
    @Cacheable(value = "productsById", key = "#id")
    @Transactional(readOnly = true)
    public ProductDTO findById(Long id) {
        return productRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
    }

    @Timed(value = "product.find.by.sku", description = "Time taken to find product by SKU")
    @Cacheable(value = "productsBySku", key = "#sku")
    @Transactional(readOnly = true)
    public ProductDTO findBySku(String sku) {
        return productRepository.findBySku(sku)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Product not found with SKU: " + sku));
    }

    @Timed(value = "product.find.all", description = "Time taken to find all products")
    @Cacheable(value = "products")
    @Transactional(readOnly = true)
    public List<ProductDTO> findAll() {
        return productRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Timed(value = "product.create", description = "Time taken to create product")
    @CacheEvict(value = {"products", "productsById", "productsBySku"}, allEntries = true)
    @Transactional
    public ProductDTO create(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new RuntimeException("Product with SKU " + request.getSku() + " already exists");
        }

        Product product = Product.builder()
                .sku(request.getSku())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stock(request.getStock())
                .category(request.getCategory())
                .brand(request.getBrand())
                .imageUrl(request.getImageUrl())
                .active(request.getActive())
                .build();

        return convertToDTO(productRepository.save(product));
    }

    @Timed(value = "product.update", description = "Time taken to update product")
    @CacheEvict(value = {"products", "productsById", "productsBySku"}, allEntries = true)
    @Transactional
    public ProductDTO update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setCategory(request.getCategory());
        product.setBrand(request.getBrand());
        product.setImageUrl(request.getImageUrl());
        product.setActive(request.getActive());

        return convertToDTO(productRepository.save(product));
    }

    @Timed(value = "product.delete", description = "Time taken to delete product")
    @CacheEvict(value = {"products", "productsById", "productsBySku"}, allEntries = true)
    @Transactional
    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    @Transactional
    public void initializeTestData(int count) {
        log.info("Initializing {} test products", count);
        for (int i = 1; i <= count; i++) {
            if (!productRepository.existsBySku("SKU-" + i)) {
                Product product = Product.builder()
                        .sku("SKU-" + i)
                        .name("Product " + i)
                        .description("Description for product " + i)
                        .price(BigDecimal.valueOf(10.0 + i))
                        .stock(100 + i)
                        .category("Category " + (i % 10))
                        .brand("Brand " + (i % 5))
                        .imageUrl("https://example.com/image-" + i + ".jpg")
                        .active(true)
                        .build();
                productRepository.save(product);
            }
        }
        log.info("Test data initialization completed");
    }

    private ProductDTO convertToDTO(Product product) {
        return ProductDTO.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .category(product.getCategory())
                .brand(product.getBrand())
                .imageUrl(product.getImageUrl())
                .active(product.getActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
