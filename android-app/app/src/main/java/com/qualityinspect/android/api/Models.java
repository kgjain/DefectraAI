package com.qualityinspect.android.api;

import java.util.List;
import java.util.Map;

/** Gson-mapped DTOs mirroring the backend wire format. */
public final class Models {

    private Models() {}

    public static class LoginRequest {
        public String username; public String password;
        public LoginRequest(String u, String p) { username = u; password = p; }
    }

    public static class AuthResponse {
        public String accessToken; public String refreshToken;
        public long expiresInSeconds; public User user;
    }

    public static class User {
        public long id; public String username; public String displayName;
        public List<String> roles; public boolean enabled;
    }

    public static class RefreshRequest {
        public String refreshToken;
        public RefreshRequest(String t) { refreshToken = t; }
    }

    public static class RegisterRequest {
        public String username; public String password; public String displayName; public String contact;
        public RegisterRequest(String u, String p, String d, String c) {
            username = u; password = p; displayName = d; contact = c;
        }
    }

    public static class OtpVerifyRequest {
        public String contact; public String otp; public String password;
        public OtpVerifyRequest(String c, String o, String p) { contact = c; otp = o; password = p; }
    }

    public static class OtpResponse {
        public String message; public String contactMasked; public String devOtp;
    }

    public static class ResendOtpRequest {
        public String contact;
        public ResendOtpRequest(String c) { contact = c; }
    }

    public static class PageResponse<T> {
        public List<T> content; public int page; public int size;
        public long totalElements; public int totalPages;
    }

    public static class Product {
        public long id; public String code; public String name; public String category;
        public String description; public Map<String, Object> specifications;
        public String status; public String createdAt; public String updatedAt;
    }

    public static class ProductRequest {
        public String code; public String name; public String category;
        public String description; public Map<String, Object> specifications;
    }

    public static class ProductUpdateRequest {
        public String code; public String name; public String category; public String description;
    }

    public static class Batch {
        public long id; public String code; public long productId; public String productName;
        public String productCode; public String productionDate; public int quantityProduced;
        public String productionLine; public String supplier; public String notes;
        public String status; public String currentDecision; public String createdAt;
    }

    public static class BatchRequest {
        public String code; public Long productId; public String productionDate;
        public Integer quantityProduced; public String productionLine; public String supplier; public String notes;
    }

    public static class BatchUpdateRequest {
        public String code; public String productionDate; public Integer quantityProduced;
        public String productionLine; public String supplier; public String notes;
    }

    public static class Inspection {
        public long id; public long batchId; public String batchCode; public long productId;
        public String productName; public String itemReference; public String evidenceScope;
        public String inspectionType; public String status; public String observations;
        public String inspector; public Long correctiveActionId; public String aiState;
        public String reviewState; public String submittedAt; public String createdAt;
    }

    public static class InspectionCreateRequest {
        public Long batchId; public String itemReference; public String evidenceScope;
        public String observations; public String idempotencyKey;
    }

    public static class InspectionDetail {
        public Inspection inspection;
        public List<Image> images;
        public List<ModelResult> modelResults;
        public List<ProcessingAttempt> attempts;
        public List<Review> reviews;
    }

    public static class Image {
        public long id; public long inspectionId; public String originalFilename;
        public String contentType; public long sizeBytes; public String sha256;
        public int version; public boolean current; public Long replacedById; public String url;
    }

    public static class ModelResult {
        public long id; public long imageId; public Long attemptId; public String predictedClass;
        public double score; public String scoreType; public String modelName; public String modelVersion;
        public String processedAt; public int durationMs; public boolean current;
    }

    public static class ProcessingAttempt {
        public long id; public long imageId; public int attemptNo; public String status;
        public String errorDetails; public Integer durationMs; public String modelName;
        public String modelVersion; public String startedAt; public String finishedAt;
    }

    public static class Review {
        public long id; public long inspectionId; public Long modelResultId; public String reviewer;
        public String decision; public String confirmedOutcome; public String reason;
        public String evidenceRequestedNote; public String reviewerRole; public String createdAt;
    }

    public static class ReviewRequest {
        public Long modelResultId; public String decision; public String confirmedOutcome;
        public String reason; public String evidenceRequestedNote;
    }

    public static class Defect {
        public long id; public String code; public long inspectionId; public long batchId;
        public String batchCode; public long productId; public String productName; public String itemKey;
        public String category; public String description; public String severity; public String status;
        public String reviewStatus; public String assignedReviewer; public String createdBy;
        public String closureReason; public String closedAt; public String createdAt; public String updatedAt;
    }

    public static class DefectDetail {
        public Defect defect;
        public List<Image> evidence;
        public List<ActionSummary> actions;
        public List<AuditEvent> history;
    }

    public static class ActionSummary {
        public long id; public String code; public String title; public String status;
        public String ownerName; public String targetDate; public boolean overdue;
    }

    public static class ActionDetail {
        public Action action;
        public String description; public String workNotes; public String completionDetails;
        public String statusReason; public Defect defect;
        public List<Inspection> followUpInspections;
        public List<AuditEvent> history;
    }

    public static class OwnerOption {
        public long id; public String username; public String displayName; public List<String> roles;
    }

    public static class FollowUpRequest {
        public String itemReference; public String evidenceScope; public String observations;
        public FollowUpRequest(String item, String scope, String obs) {
            itemReference = item; evidenceScope = scope; observations = obs;
        }
    }

    public static class DefectCreateRequest {
        public Long inspectionId; public String description; public String severity; public String category;
    }

    public static class DefectUpdateRequest {
        public String description; public String severity; public String category;
        public String status; public String reason; public String reviewStatus;
    }

    public static class Action {
        public long id; public String code; public long defectId; public String defectCode;
        public String title; public long ownerId; public String ownerName; public String targetDate;
        public String status; public boolean overdue; public String createdAt; public String updatedAt;
    }

    public static class ActionCreateRequest {
        public Long defectId; public String title; public String description;
        public Long ownerId; public String targetDate;
    }

    public static class ActionUpdateRequest {
        public String status; public String workNotes; public String completionDetails;
        public String reason; public String targetDate;
    }

    public static class DashboardSummary {
        public int activeProducts; public int activeBatches; public int distinctItemsInspected;
        public int distinctItemsReviewed; public int pendingReviews; public int pendingProcessing;
        public int distinctItemsWithConfirmedDefects; public Double confirmedDefectRate;
        public int rateNumerator; public int rateDenominator;
        public int openCorrectiveActions; public int overdueCorrectiveActions;
    }

    public static class TrendPoint {
        public String date; public long inspectionsSubmitted; public long confirmedDefects;
    }

    public static class TrendResponse {
        public int days; public List<TrendPoint> points;
    }

    public static class RecentActivity {
        public List<Inspection> recentInspections;
        public List<Defect> recentlyConfirmedDefects;
        public List<Action> actionsNeedingAttention;
    }

    public static class BatchQuality {
        public long batchId; public String batchCode; public String productName; public int quantityProduced;
        public long distinctItemsInspected; public long distinctItemsReviewed;
        public long distinctItemsWithConfirmedDefects; public long pendingProcessing;
        public long pendingReviews; public long openCorrectiveActions;
        public Double confirmedDefectRate; public long rateNumerator; public long rateDenominator;
        public String currentDecision; public List<Decision> decisionHistory;
    }

    public static class ReviewQueueItem {
        public long inspectionId; public String batchCode; public String productCode;
        public String productName; public String itemReference; public String status;
        public String aiState; public String reviewState; public int priorityScore;
        public List<Reason> reasons; public String submittedAt;

        public static class Reason {
            public String factor; public int weight; public String detail;
        }
    }

    public static class DecisionRequest {
        public String decision; public String reason;
    }

    public static class Decision {
        public long id; public long batchId; public String decision; public String reason;
        public String decidedBy; public String decidedAt;
    }

    public static class AiReport {
        public long id; public String reportType; public String requestText; public String responseText;
        public List<String> facts; public Map<String, Object> grounding; public List<String> suggestions;
        public List<String> missingEvidence; public String mode; public boolean generated;
        public String modelProvider; public String modelName; public String createdAt;
    }

    public static class AiReportRequest {
        public Long batchId; public String reportType; public String requestText;
    }

    public static class AiQuestionRequest {
        public String question; public Long batchId;
        public AiQuestionRequest(String question, Long batchId) {
            this.question = question; this.batchId = batchId;
        }
    }

    public static class AuditEvent {
        public long id; public String entityType; public String entityId; public String action;
        public String actor; public String reason; public Map<String, Object> details;
        public String occurredAt;
    }

    // ---- user administration (QUALITY_MANAGER only) ----

    public static class AdminUser {
        public long id; public String username; public String displayName;
        public List<String> roles; public boolean enabled;
    }

    public static class CreateUserRequest {
        public String username; public String password; public String displayName;
        public List<String> roles;
        public CreateUserRequest(String u, String p, String d, List<String> r) {
            username = u; password = p; displayName = d; roles = r;
        }
    }

    public static class EnabledRequest {
        public boolean enabled;
        public EnabledRequest(boolean e) { enabled = e; }
    }

    public static class RolesRequest {
        public List<String> roles;
        public RolesRequest(List<String> r) { roles = r; }
    }

    public static class PasswordRequest {
        public String password;
        public PasswordRequest(String p) { password = p; }
    }

    public static class ApiError {
        public int status; public String error; public String message; public String path;
        public List<FieldError> fieldErrors;
        public static class FieldError { public String field; public String message; }
    }
}
