package com.cacheperf.cache_benchmark.controller;

import com.cacheperf.cache_benchmark.model.dto.ProductDTO;
import com.cacheperf.cache_benchmark.model.dto.ProductRequest;
import com.cacheperf.cache_benchmark.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductDTO>> getAllProducts() {
        return ResponseEntity.ok(productService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<ProductDTO> getProductBySku(@PathVariable String sku) {
        return ResponseEntity.ok(productService.findBySku(sku));
    }

    @PostMapping
    public ResponseEntity<ProductDTO> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductDTO created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> updateProduct(@PathVariable Long id,
                                                     @Valid @RequestBody ProductRequest request) {
        ProductDTO updated = productService.update(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/init/{count}")
    public ResponseEntity<String> initializeTestData(@PathVariable int count) {
        productService.initializeTestData(count);
        return ResponseEntity.ok("Initialized " + count + " test products");
    }
}
