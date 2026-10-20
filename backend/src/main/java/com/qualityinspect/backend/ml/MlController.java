package com.qualityinspect.backend.ml;

import com.qualityinspect.backend.inspection.InspectionDtos.ModelResultResponse;
import com.qualityinspect.backend.inspection.InspectionDtos.ProcessingAttemptResponse;
import com.qualityinspect.backend.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MlController {

    private final MlService mlService;

    @PostMapping("/api/v1/images/{id}/process")
    @PreAuthorize("hasAnyRole('INSPECTOR','SUPERVISOR','QUALITY_MANAGER')")
    public ResponseEntity<ProcessingAttemptResponse> process(
            @PathVariable long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(mlService.requestProcessing(id, principal));
    }

    @GetMapping("/api/v1/images/{id}/results")
    public List<ModelResultResponse> results(@PathVariable long id) {
        return mlService.results(id);
    }

    @GetMapping("/api/v1/processing-attempts/{id}")
    public ProcessingAttemptResponse attempt(@PathVariable long id) {
        return mlService.attempt(id);
    }
}
