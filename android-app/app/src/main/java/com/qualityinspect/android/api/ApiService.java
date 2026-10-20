package com.qualityinspect.android.api;

import com.qualityinspect.android.api.Models.*;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {

    // ---- auth ----
    @POST("api/v1/auth/login")
    Call<AuthResponse> login(@Body LoginRequest req);

    @POST("api/v1/auth/register")
    Call<OtpResponse> register(@Body RegisterRequest req);

    @POST("api/v1/auth/register/verify")
    Call<AuthResponse> verifyRegistration(@Body OtpVerifyRequest req);

    @POST("api/v1/auth/register/resend")
    Call<OtpResponse> resendOtp(@Body ResendOtpRequest req);

    @POST("api/v1/auth/refresh")
    Call<AuthResponse> refresh(@Body RefreshRequest req);

    @POST("api/v1/auth/logout")
    Call<Void> logout(@Body RefreshRequest req);

    @GET("api/v1/auth/me")
    Call<User> me();

    // ---- products ----
    @GET("api/v1/products")
    Call<PageResponse<Product>> products(
            @Query("search") String search, @Query("category") String category,
            @Query("status") String status, @Query("page") int page, @Query("size") int size);

    @GET("api/v1/products/{id}")
    Call<Product> product(@Path("id") long id);

    @POST("api/v1/products")
    Call<Product> createProduct(@Body ProductRequest req);

    @PATCH("api/v1/products/{id}")
    Call<Product> updateProduct(@Path("id") long id, @Body ProductUpdateRequest req);

    @POST("api/v1/products/{id}/archive")
    Call<Product> archiveProduct(@Path("id") long id);

    // ---- batches ----
    @GET("api/v1/batches")
    Call<PageResponse<Batch>> batches(
            @Query("search") String search, @Query("productId") Long productId,
            @Query("status") String status, @Query("page") int page, @Query("size") int size);

    @GET("api/v1/batches/{id}")
    Call<Batch> batch(@Path("id") long id);

    @POST("api/v1/batches")
    Call<Batch> createBatch(@Body BatchRequest req);

    @PATCH("api/v1/batches/{id}")
    Call<Batch> updateBatch(@Path("id") long id, @Body BatchUpdateRequest req);

    @POST("api/v1/batches/{id}/archive")
    Call<Batch> archiveBatch(@Path("id") long id);

    @GET("api/v1/batches/{id}/quality")
    Call<BatchQuality> batchQuality(@Path("id") long id);

    @POST("api/v1/batches/{id}/decisions")
    Call<Decision> decide(@Path("id") long id, @Body DecisionRequest req);

    // ---- inspections ----
    @GET("api/v1/inspections")
    Call<PageResponse<Inspection>> inspections(
            @Query("batchId") Long batchId, @Query("productId") Long productId,
            @Query("status") String status, @Query("itemReference") String itemReference,
            @Query("reviewState") String reviewState, @Query("aiState") String aiState,
            @Query("page") int page, @Query("size") int size);

    @GET("api/v1/inspections/{id}")
    Call<InspectionDetail> inspection(@Path("id") long id);

    @POST("api/v1/inspections")
    Call<Inspection> createInspection(@Body InspectionCreateRequest req);

    @POST("api/v1/inspections/{id}/submit")
    Call<Inspection> submitInspection(@Path("id") long id);

    @Multipart
    @POST("api/v1/inspections/{id}/images")
    Call<Image> uploadImage(@Path("id") long id, @Part MultipartBody.Part file);

    @POST("api/v1/images/{id}/process")
    Call<ProcessingAttempt> processImage(@Path("id") long id);

    @GET("api/v1/images/{id}/results")
    Call<java.util.List<ModelResult>> imageResults(@Path("id") long id);

    @Streaming
    @GET("api/v1/images/{id}")
    Call<ResponseBody> imageContent(@Path("id") long id);

    @POST("api/v1/inspections/{id}/reviews")
    Call<Review> reviewInspection(@Path("id") long id, @Body ReviewRequest req);

    // ---- review queue ----
    @GET("api/v1/review-queue")
    Call<java.util.List<ReviewQueueItem>> reviewQueue();

    // ---- defects ----
    @GET("api/v1/defects")
    Call<PageResponse<Defect>> defects(
            @Query("search") String search, @Query("batchId") Long batchId,
            @Query("productId") Long productId, @Query("status") String status,
            @Query("severity") String severity, @Query("category") String category,
            @Query("from") String from, @Query("to") String to,
            @Query("page") int page, @Query("size") int size);

    @GET("api/v1/defects/{id}")
    Call<DefectDetail> defect(@Path("id") long id);

    @POST("api/v1/defects")
    Call<Defect> createDefect(@Body DefectCreateRequest req);

    @PATCH("api/v1/defects/{id}")
    Call<Defect> updateDefect(@Path("id") long id, @Body DefectUpdateRequest req);

    // ---- actions ----
    @GET("api/v1/actions")
    Call<PageResponse<Action>> actions(
            @Query("search") String search, @Query("defectId") Long defectId,
            @Query("ownerId") Long ownerId, @Query("status") String status,
            @Query("overdue") boolean overdue, @Query("page") int page, @Query("size") int size);

    @GET("api/v1/actions/{id}")
    Call<ActionDetail> action(@Path("id") long id);

    @GET("api/v1/actions/owners")
    Call<java.util.List<OwnerOption>> actionOwners(@Query("role") String role);

    @POST("api/v1/actions/{id}/reinspections")
    Call<Inspection> createFollowUp(@Path("id") long id, @Body FollowUpRequest req);

    @POST("api/v1/actions")
    Call<Action> createAction(@Body ActionCreateRequest req);

    @PATCH("api/v1/actions/{id}")
    Call<Action> updateAction(@Path("id") long id, @Body ActionUpdateRequest req);

    // ---- user administration (QUALITY_MANAGER only) ----
    @GET("api/v1/users/admin")
    Call<java.util.List<AdminUser>> adminUsers();

    @POST("api/v1/users")
    Call<AdminUser> createUser(@Body CreateUserRequest req);

    @PATCH("api/v1/users/{id}/enabled")
    Call<AdminUser> setUserEnabled(@Path("id") long id, @Body EnabledRequest req);

    @PUT("api/v1/users/{id}/roles")
    Call<AdminUser> setUserRoles(@Path("id") long id, @Body RolesRequest req);

    @PUT("api/v1/users/{id}/password")
    Call<Void> resetUserPassword(@Path("id") long id, @Body PasswordRequest req);

    // ---- dashboard ----
    @GET("api/v1/dashboard/summary")
    Call<DashboardSummary> dashboardSummary();

    @GET("api/v1/dashboard/trends")
    Call<TrendResponse> dashboardTrends(@Query("days") int days);

    @GET("api/v1/dashboard/recent")
    Call<RecentActivity> dashboardRecent();

    // ---- AI ----
    @POST("api/v1/ai/reports/batch-summary")
    Call<AiReport> aiBatchSummary(@Body AiReportRequest req);

    @POST("api/v1/ai/questions")
    Call<AiReport> aiQuestion(@Body AiQuestionRequest req);

    // ---- audit ----
    @GET("api/v1/audit-events")
    Call<PageResponse<AuditEvent>> auditEvents(
            @Query("entityType") String entityType, @Query("page") int page, @Query("size") int size);

    // ---- search ----
    @GET("api/v1/search")
    Call<SearchResponse> search(@Query("q") String q, @Query("types") String types,
                                @Query("page") int page, @Query("size") int size);

    class SearchResponse {
        public PageResponse<Product> products;
        public PageResponse<Batch> batches;
        public PageResponse<Inspection> inspections;
        public PageResponse<Defect> defects;
        public PageResponse<Action> actions;
    }

    @GET("api/v1/health")
    Call<ResponseBody> health();
}
