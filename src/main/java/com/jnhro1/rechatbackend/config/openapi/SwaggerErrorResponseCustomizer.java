package com.jnhro1.rechatbackend.config.openapi;

import com.jnhro1.rechatbackend.common.exception.ApiErrorCodes;
import com.jnhro1.rechatbackend.common.exception.ErrorCode;
import com.jnhro1.rechatbackend.common.response.ApiResponse;
import com.jnhro1.rechatbackend.common.response.ErrorResponse;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

@Component
public class SwaggerErrorResponseCustomizer implements OperationCustomizer {

    private static final String APPLICATION_JSON = "application/json";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        ApiErrorCodes annotation = handlerMethod.getMethodAnnotation(ApiErrorCodes.class);
        if (annotation == null || annotation.value().length == 0) {
            return operation;
        }

        // 상태코드별로 묶는다(한 status에는 한 응답만 매핑 가능하므로).
        Map<HttpStatus, List<ErrorCode>> byStatus = Arrays.stream(annotation.value())
                .collect(Collectors.groupingBy(ErrorCode::getStatus, LinkedHashMap::new, Collectors.toList()));

        ApiResponses responses = operation.getResponses();
        byStatus.forEach((status, codes) -> responses.addApiResponse(
                String.valueOf(status.value()),
                buildResponse(codes)
        ));
        return operation;
    }

    private io.swagger.v3.oas.models.responses.ApiResponse buildResponse(List<ErrorCode> codes) {
        MediaType mediaType = new MediaType();
        for (ErrorCode code : codes) {
            // FYI: 예시 값으로 실제 ApiResponse.failure 봉투를 그대로 직렬화 → 문서와 실제 응답 형태가 항상 일치.
            Example example = new Example()
                    .summary(code.getDefaultMessage())
                    .value(ApiResponse.failure(ErrorResponse.of(code)));
            mediaType.addExamples(code.getCode(), example);
        }
        Content content = new Content().addMediaType(APPLICATION_JSON, mediaType);
        String description = codes.stream().map(ErrorCode::getCode).collect(Collectors.joining(", "));
        return new io.swagger.v3.oas.models.responses.ApiResponse()
                .description(description)
                .content(content);
    }
}
