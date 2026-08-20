package spring.route.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 수직 이동 정책이 어떤 간선을 남기는지. 파이썬 building_queries._vertical_allows와 같아야 한다. */
class GraphServiceTest {

    @Test
    @DisplayName("층 내부 간선은 정책과 무관하게 항상 포함된다")
    void intraFloorEdgesAlwaysPass() {
        assertThat(GraphService.verticalAllows("elevator", null)).isTrue();
        assertThat(GraphService.verticalAllows("escalator", null)).isTrue();
        assertThat(GraphService.verticalAllows("auto", null)).isTrue();
    }

    @Test
    @DisplayName("정책이 수단을 지정하면 그 수단만 남는다")
    void policyFiltersTransferMode() {
        assertThat(GraphService.verticalAllows("elevator", "elevator")).isTrue();
        assertThat(GraphService.verticalAllows("elevator", "escalator")).isFalse();
        assertThat(GraphService.verticalAllows("escalator", "escalator")).isTrue();
        assertThat(GraphService.verticalAllows("escalator", "elevator")).isFalse();
    }

    @Test
    @DisplayName("auto는 둘 다 남긴다 — 비용 모델이 층수에 따라 고른다")
    void autoKeepsBoth() {
        assertThat(GraphService.verticalAllows("auto", "elevator")).isTrue();
        assertThat(GraphService.verticalAllows("auto", "escalator")).isTrue();
    }
}
