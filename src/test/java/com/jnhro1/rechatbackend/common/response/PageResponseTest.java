package com.jnhro1.rechatbackend.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    @Test
    @DisplayName("from(Page) - 페이지 메타(page/size/total/totalPages/hasNext)를 평탄화한다")
    void from_flattensPageMeta() {
        // 전체 5건, page=0, size=2 → 총 3페이지, 다음 페이지 있음
        List<String> content = List.of("a", "b");
        PageImpl<String> page = new PageImpl<>(content, PageRequest.of(0, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    @DisplayName("from(Page) - 마지막 페이지는 hasNext=false")
    void from_lastPageHasNoNext() {
        PageImpl<String> page = new PageImpl<>(List.of("e"), PageRequest.of(2, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.hasNext()).isFalse();
    }
}
