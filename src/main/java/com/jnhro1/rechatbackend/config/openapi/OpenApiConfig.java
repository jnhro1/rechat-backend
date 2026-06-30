package com.jnhro1.rechatbackend.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rechatOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Rechat API")
                        .description("""
                                1:1 실시간 채팅 및 이벤트 기반 상태 복원 API.

                                - 모든 응답은 ApiResponse<T> 봉투로 감싼다.
                                - 상태 변경 요청은 Idempotency-Key 헤더를 지원한다.
                                - 이벤트 정렬·복원 기준은 server_sequence.
                                """)
                        .version("v1")
                        .license(new License().name("Take-home assignment")))
                .servers(List.of(
                        new Server().url("/").description("현재 서버")
                ));
    }
}
