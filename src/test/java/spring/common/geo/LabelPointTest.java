package spring.common.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 파이썬 label_point.py와 같은 점을 고르는지 대조한다. 기대값은 원본 구현을 실제로 돌려 뽑았다. */
class LabelPointTest {

    /** ㄱ자 폴리곤 — 무게중심이 실제로 넓은 쪽을 벗어나는 대표 사례다. */
    private static final List<double[]> L_SHAPE = List.of(
            new double[] {0.0, 0.0},
            new double[] {10.0, 0.0},
            new double[] {10.0, 3.0},
            new double[] {3.0, 3.0},
            new double[] {3.0, 10.0},
            new double[] {0.0, 10.0});

    @Test
    @DisplayName("ㄱ자 폴리곤에서 무게중심과 다른 점을 고른다 — 그게 이 함수의 존재 이유다")
    void concaveShapePicksPoleOfInaccessibility() {
        double[] label = LabelPoint.of(L_SHAPE);
        double[] centroid = LabelPoint.polygonCentroid(L_SHAPE);

        assertThat(label[0]).isCloseTo(1.7592592592592591, within(1e-9));
        assertThat(label[1]).isCloseTo(1.7592592592592591, within(1e-9));
        assertThat(centroid[0]).isCloseTo(3.5588235294117645, within(1e-12));
        // 두 점이 1.8m 넘게 어긋난다. 도면에서 1m면 아이콘 몇 개 폭이다.
        assertThat(Math.hypot(label[0] - centroid[0], label[1] - centroid[1])).isGreaterThan(1.8);
    }

    @Test
    @DisplayName("볼록 4각형은 격자 탐색 없이 무게중심으로 끝낸다")
    void convexQuadrilateralUsesCentroid() {
        double[] rectangle = LabelPoint.of(List.of(
                new double[] {0.0, 0.0}, new double[] {10.0, 0.0}, new double[] {10.0, 6.0}, new double[] {0.0, 6.0}));
        assertThat(rectangle[0]).isCloseTo(5.0, within(1e-12));
        assertThat(rectangle[1]).isCloseTo(3.0, within(1e-12));

        // 사다리꼴도 같은 경로를 탄다.
        double[] trapezoid = LabelPoint.of(List.of(
                new double[] {0.0, 0.0}, new double[] {10.0, 0.0}, new double[] {7.0, 5.0}, new double[] {2.0, 5.0}));
        assertThat(trapezoid[0]).isCloseTo(4.7777777777777777, within(1e-9));
        assertThat(trapezoid[1]).isCloseTo(2.2222222222222223, within(1e-9));
    }

    @Test
    @DisplayName("닫힌 링을 넘겨도 볼록 4각형으로 알아본다")
    void closedRingIsRecognized() {
        double[] closed = LabelPoint.of(List.of(
                new double[] {0.0, 0.0},
                new double[] {10.0, 0.0},
                new double[] {10.0, 6.0},
                new double[] {0.0, 6.0},
                new double[] {0.0, 0.0}));
        assertThat(closed[0]).isCloseTo(5.0, within(1e-12));
        assertThat(closed[1]).isCloseTo(3.0, within(1e-12));
    }

    @Test
    @DisplayName("점이 모자라거나 납작해도 죽지 않는다 — 라벨을 안 그리는 것보다 낫다")
    void degenerateInputFallsBack() {
        assertThat(LabelPoint.of(List.of())).containsExactly(0.0, 0.0);
        assertThat(LabelPoint.of(List.of(new double[] {2.0, 4.0}))).containsExactly(2.0, 4.0);
        // 한 직선 위에 놓인 점들(면적 0) — 꼭짓점 평균으로 떨어진다
        assertThat(LabelPoint.of(List.of(new double[] {0.0, 0.0}, new double[] {2.0, 0.0}, new double[] {4.0, 0.0}))[0])
                .isCloseTo(2.0, within(1e-12));
    }

    /**
     * 무게중심은 첫 꼭짓점 기준 상대 좌표로 계산해야 한다. 절대 경위도로 계산하면 외적 항이 진짜
     * 면적보다 12자리 커서 상쇄에 유효자리를 다 잃고, 결과가 폴리곤에서 수 km 벗어난다.
     */
    @Test
    @DisplayName("경위도 규모에서도 무게중심이 폴리곤 안에 있다")
    void centroidSurvivesWgs84Magnitudes() {
        double lng = 126.9780;
        double lat = 37.5665;
        double size = 0.00005; // 약 5m
        double[] centroid = LabelPoint.polygonCentroid(List.of(
                new double[] {lng, lat},
                new double[] {lng + size, lat},
                new double[] {lng + size, lat + size},
                new double[] {lng, lat + size}));

        assertThat(centroid[0]).isCloseTo(lng + size / 2, within(1e-12));
        assertThat(centroid[1]).isCloseTo(lat + size / 2, within(1e-12));
    }
}
