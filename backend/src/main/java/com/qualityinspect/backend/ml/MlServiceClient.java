package com.qualityinspect.backend.ml;

import com.qualityinspect.backend.common.error.ApiException.ServiceUnavailableException;
import com.qualityinspect.backend.config.AppProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.MultipartBodyBuilder;

import java.time.Duration;

/** Server-side HTTP client for the ML service. The Android app never calls it directly. */
@Component
public class MlServiceClient {

    private final AppProperties props;
    private final RestClient restClient;

    public MlServiceClient(AppProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.getMl().getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.getMl().getReadTimeoutMs()));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public MlPredictResponse predict(byte[] imageBytes, String filename, String contentType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return StringUtils.hasText(filename) ? filename : "image.jpg";
            }
        }, MediaType.parseMediaType(contentType));

        try {
            MlPredictResponse response = restClient.post()
                    .uri(props.getMl().getUrl() + "/predict")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(MlPredictResponse.class);
            if (response == null || response.predictedClass() == null) {
                throw new ServiceUnavailableException("ML service returned an empty response");
            }
            return response;
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new ServiceUnavailableException("ML service is unavailable: " + safeMessage(ex));
        }
    }

    private String safeMessage(Exception ex) {
        String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return msg.length() > 300 ? msg.substring(0, 300) : msg;
    }
}
