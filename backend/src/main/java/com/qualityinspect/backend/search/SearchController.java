package com.qualityinspect.backend.search;

import com.qualityinspect.backend.action.ActionDtos.ActionResponse;
import com.qualityinspect.backend.action.ActionService;
import com.qualityinspect.backend.batch.BatchDtos.BatchResponse;
import com.qualityinspect.backend.batch.BatchService;
import com.qualityinspect.backend.common.web.PageResponse;
import com.qualityinspect.backend.defect.DefectDtos.DefectResponse;
import com.qualityinspect.backend.defect.DefectService;
import com.qualityinspect.backend.inspection.InspectionDtos.InspectionResponse;
import com.qualityinspect.backend.inspection.InspectionService;
import com.qualityinspect.backend.product.ProductDtos.ProductResponse;
import com.qualityinspect.backend.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Set;

/** Cross-resource search used by the mobile "Search & filters" screen. */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private static final Set<String> ALL = Set.of("PRODUCT", "BATCH", "INSPECTION", "DEFECT", "ACTION");

    private final ProductService productService;
    private final BatchService batchService;
    private final InspectionService inspectionService;
    private final DefectService defectService;
    private final ActionService actionService;

    public record SearchResponse(
            PageResponse<ProductResponse> products,
            PageResponse<BatchResponse> batches,
            PageResponse<InspectionResponse> inspections,
            PageResponse<DefectResponse> defects,
            PageResponse<ActionResponse> actions) {}

    @GetMapping
    public SearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "PRODUCT,BATCH,INSPECTION,DEFECT,ACTION") String types,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Set<String> enabled = parseTypes(types);
        String term = q == null || q.isBlank() ? null : q.trim();
        int safeSize = Math.min(Math.max(size, 1), 50);

        PageResponse<ProductResponse> products = enabled.contains("PRODUCT") && term != null
                ? productService.list(term, null, null, page, safeSize)
                : emptyProducts(page, safeSize);
        PageResponse<BatchResponse> batches = enabled.contains("BATCH") && term != null
                ? batchService.list(term, null, null, null, null, page, safeSize)
                : empty(page, safeSize, BatchResponse.class);
        PageResponse<InspectionResponse> inspections = enabled.contains("INSPECTION") && term != null
                ? inspectionService.list(null, null, null, null, term, null, null, null, null, page, safeSize)
                : empty(page, safeSize, InspectionResponse.class);
        PageResponse<DefectResponse> defects = enabled.contains("DEFECT") && term != null
                ? defectService.list(term, null, null, null, null, null, null, null, page, safeSize)
                : empty(page, safeSize, DefectResponse.class);
        PageResponse<ActionResponse> actions = enabled.contains("ACTION") && term != null
                ? actionService.list(term, null, null, null, false, page, safeSize)
                : empty(page, safeSize, ActionResponse.class);

        return new SearchResponse(products, batches, inspections, defects, actions);
    }

    private Set<String> parseTypes(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        Set<String> parsed = new java.util.HashSet<>();
        for (String part : raw.toUpperCase(Locale.ROOT).split(",")) {
            String t = part.trim();
            if (ALL.contains(t)) {
                parsed.add(t);
            }
        }
        return parsed.isEmpty() ? ALL : parsed;
    }

    private PageResponse<ProductResponse> emptyProducts(int page, int size) {
        return new PageResponse<>(java.util.List.of(), page, size, 0, 0);
    }

    private <T> PageResponse<T> empty(int page, int size, Class<T> type) {
        return new PageResponse<>(java.util.List.of(), page, size, 0, 0);
    }
}
