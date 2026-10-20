package com.qualityinspect.backend.batch;

import com.qualityinspect.backend.batch.BatchDtos.*;
import com.qualityinspect.backend.common.audit.AuditService;
import com.qualityinspect.backend.common.error.ApiException.*;
import com.qualityinspect.backend.common.web.PageResponse;
import com.qualityinspect.backend.decision.BatchDecisionRepository;
import com.qualityinspect.backend.product.ProductEntity;
import com.qualityinspect.backend.product.ProductRepository;
import com.qualityinspect.backend.security.AuthPrincipal;
import com.qualityinspect.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BatchService {

    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final BatchDecisionRepository decisionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<BatchResponse> list(String search, Long productId, String status,
                                            LocalDate from, LocalDate to, int page, int size) {
        Page<BatchEntity> result = batchRepository.search(
                blankToNull(search),
                productId,
                blankToNull(status),
                from, to,
                PageRequest.of(page, Math.min(size, 100)));
        return PageResponse.from(result.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public BatchResponse get(long id) {
        return toResponse(require(id));
    }

    @Transactional
    public BatchResponse create(BatchRequest request, AuthPrincipal principal) {
        String code = request.code().trim();
        if (batchRepository.existsByCode(code)) {
            throw new ConflictException("Batch code '" + code + "' already exists");
        }
        ProductEntity product = productRepository.findById(request.productId())
                .orElseThrow(() -> new NotFoundException("Product " + request.productId() + " not found"));
        if ("ARCHIVED".equals(product.getStatus())) {
            throw new ConflictException("Cannot create a batch for an archived product");
        }

        BatchEntity entity = new BatchEntity();
        entity.setCode(code);
        entity.setProductId(product.getId());
        entity.setProductionDate(request.productionDate());
        entity.setQuantityProduced(request.quantityProduced());
        entity.setProductionLine(trimToNull(request.productionLine()));
        entity.setSupplier(trimToNull(request.supplier()));
        entity.setNotes(request.notes());
        entity.setCreatedBy(principal.getId());
        entity = batchRepository.save(entity);

        auditService.record("BATCH", entity.getId(), "BATCH_CREATED", null,
                Map.of("code", entity.getCode(), "productId", product.getId()));
        return toResponse(entity);
    }

    @Transactional
    public BatchResponse update(long id, BatchUpdateRequest request) {
        BatchEntity entity = require(id);
        if (request.code() != null && !request.code().isBlank()) {
            String code = request.code().trim();
            if (!code.equals(entity.getCode()) && batchRepository.existsByCodeAndIdNot(code, id)) {
                throw new ConflictException("Batch code '" + code + "' already exists");
            }
            entity.setCode(code);
        }
        if (request.productionDate() != null) entity.setProductionDate(request.productionDate());
        if (request.quantityProduced() != null) entity.setQuantityProduced(request.quantityProduced());
        if (request.productionLine() != null) entity.setProductionLine(trimToNull(request.productionLine()));
        if (request.supplier() != null) entity.setSupplier(trimToNull(request.supplier()));
        if (request.notes() != null) entity.setNotes(request.notes());
        entity = batchRepository.save(entity);
        auditService.record("BATCH", entity.getId(), "BATCH_UPDATED", null, Map.of("code", entity.getCode()));
        return toResponse(entity);
    }

    @Transactional
    public BatchResponse archive(long id) {
        BatchEntity entity = require(id);
        if ("ARCHIVED".equals(entity.getStatus())) {
            throw new ConflictException("Batch is already archived");
        }
        entity.setStatus("ARCHIVED");
        entity = batchRepository.save(entity);
        // historical inspections remain fully readable for archived batches
        auditService.record("BATCH", entity.getId(), "BATCH_ARCHIVED", "Batch archived", null);
        return toResponse(entity);
    }

    BatchEntity require(long id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Batch " + id + " not found"));
    }

    private BatchResponse toResponse(BatchEntity entity) {
        ProductEntity product = productRepository.findById(entity.getProductId()).orElse(null);
        String creator = entity.getCreatedBy() == null ? null :
                userRepository.findById(entity.getCreatedBy()).map(u -> u.getUsername()).orElse(null);
        String decision = decisionRepository.findTopByBatchIdOrderByDecidedAtDesc(entity.getId())
                .map(d -> d.getDecision()).orElse(null);
        return new BatchResponse(
                entity.getId(), entity.getCode(),
                entity.getProductId(),
                product == null ? "(archived)" : product.getName(),
                product == null ? "(archived)" : product.getCode(),
                entity.getProductionDate(), entity.getQuantityProduced(),
                entity.getProductionLine(), entity.getSupplier(), entity.getNotes(),
                entity.getStatus(), decision, creator, entity.getCreatedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
