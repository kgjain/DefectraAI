package com.qualityinspect.backend.product;

import com.qualityinspect.backend.common.web.PageResponse;
import com.qualityinspect.backend.product.ProductDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.qualityinspect.backend.security.AuthPrincipal;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productService.list(search, category, status, page, size);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable long id) {
        return productService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('QUALITY_MANAGER')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request,
                                                  @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request, principal));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('QUALITY_MANAGER')")
    public ProductResponse update(@PathVariable long id, @Valid @RequestBody ProductUpdateRequest request) {
        return productService.update(id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('QUALITY_MANAGER')")
    public ProductResponse archive(@PathVariable long id) {
        return productService.archive(id);
    }
}
