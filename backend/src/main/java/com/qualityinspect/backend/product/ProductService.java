package com.qualityinspect.backend.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualityinspect.backend.auth.AuthDtos.UserResponse;
import com.qualityinspect.backend.common.audit.AuditService;
import com.qualityinspect.backend.common.error.ApiException.*;
import com.qualityinspect.backend.common.web.PageResponse;
import com.qualityinspect.backend.product.ProductDtos.*;
import com.qualityinspect.backend.security.AuthPrincipal;
import com.qualityinspect.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(String search, String category, String status, int page, int size) {
        String s = blankToNull(search);
        Page<ProductEntity> result = productRepository.search(
                s == null ? null : s.toLowerCase(),
                blankToNull(category),
                blankToNull(status),
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "updatedAt")));
        return PageResponse.from(result.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(long id) {
        return toResponse(require(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request, AuthPrincipal principal) {
        String code = request.code().trim();
        if (productRepository.existsByCode(code)) {
            throw new ConflictException("Product code '" + code + "' already exists");
        }
        ProductEntity entity = new ProductEntity();
        entity.setCode(code);
        entity.setName(request.name().trim());
        entity.setCategory(request.category().trim());
        entity.setDescription(request.description());
        entity.setSpecifications(toJson(request.specifications()));
        entity.setCreatedBy(principal.getId());
        entity = productRepository.save(entity);

        auditService.record("PRODUCT", entity.getId(), "PRODUCT_CREATED", null,
                Map.of("code", entity.getCode(), "name", entity.getName()));
        return toResponse(entity);
    }

    @Transactional
    public ProductResponse update(long id, ProductUpdateRequest request) {
        ProductEntity entity = require(id);
        if (request.code() != null && !request.code().isBlank()) {
            String code = request.code().trim();
            if (!code.equals(entity.getCode()) && productRepository.existsByCodeAndIdNot(code, id)) {
                throw new ConflictException("Product code '" + code + "' already exists");
            }
            entity.setCode(code);
        }
        if (request.name() != null && !request.name().isBlank()) {
            entity.setName(request.name().trim());
        }
        if (request.category() != null && !request.category().isBlank()) {
            entity.setCategory(request.category().trim());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.specifications() != null) {
            entity.setSpecifications(toJson(request.specifications()));
        }
        entity = productRepository.save(entity);
        auditService.record("PRODUCT", entity.getId(), "PRODUCT_UPDATED", null,
                Map.of("code", entity.getCode()));
        return toResponse(entity);
    }

    @Transactional
    public ProductResponse archive(long id) {
        ProductEntity entity = require(id);
        if ("ARCHIVED".equals(entity.getStatus())) {
            throw new ConflictException("Product is already archived");
        }
        entity.setStatus("ARCHIVED");
        entity = productRepository.save(entity);
        // historical inspections/batches keep referencing this product - archival never deletes
        auditService.record("PRODUCT", entity.getId(), "PRODUCT_ARCHIVED", "Product archived", null);
        return toResponse(entity);
    }

    ProductEntity require(long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    private ProductResponse toResponse(ProductEntity entity) {
        String creator = null;
        if (entity.getCreatedBy() != null) {
            creator = userRepository.findById(entity.getCreatedBy())
                    .map(u -> u.getUsername()).orElse(null);
        }
        return new ProductResponse(
                entity.getId(), entity.getCode(), entity.getName(), entity.getCategory(),
                entity.getDescription(), entity.specificationsMap(objectMapper), entity.getStatus(),
                creator, entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private String toJson(Map<String, Object> specs) {
        if (specs == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(specs);
        } catch (Exception e) {
            throw new BadRequestException("Specifications could not be serialized");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
