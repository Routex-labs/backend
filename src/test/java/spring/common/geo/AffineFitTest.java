package spring.common.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import spring.common.geometry.LatLng;

/**
 * 파이썬 georeference.py(numpy lstsq)와 같은 값을 내는지 대조한다.
 *
 * <p>기대값은 원본 저장소의 구현을 실제로 돌려 뽑았다. 두 구현은 푸는 방법이 다르므로
 * (SVD vs 중심화 정규방정식) 비트 단위로 같을 수 없다 — 상대오차 1e-9 안이면 같다고 본다.
 * 위경도 1e-9도는 약 0.1mm다.
 */
class AffineFitTest {

    /** 축별 스케일이 다르고 살짝 기울어진, 잡음 있는 대응점 5개. */
    private static final List<PointPair> WGS84_PAIRS = List.of(
            new PointPair(0.0, 0.0, 126.9780, 37.56650),
            new PointPair(100.0, 0.0, 126.97913, 37.56662),
            new PointPair(0.0, 80.0, 126.97788, 37.56721),
            new PointPair(100.0, 80.0, 126.97921, 37.56733),
            new PointPair(50.0, 40.0, 126.978545, 37.566915));

    @Test
    @DisplayName("wgs84 피팅 계수가 파이썬 구현과 일치한다")
    void fitWgs84MatchesPython() {
        GeoTransform transform = AffineFit.fitWgs84(WGS84_PAIRS);

        assertThat(transform.a()).isCloseTo(9.7494945679824124e-06, within(1e-15));
        assertThat(transform.b()).isCloseTo(-1.9816045834462699e-07, within(1e-15));
        assertThat(transform.c()).isCloseTo(1.2000000000497657e-06, within(1e-15));
        assertThat(transform.d()).isCloseTo(8.8749999997331208e-06, within(1e-15));
        assertThat(transform.tx()).isCloseTo(100.64803368194748, within(1e-9));
        assertThat(transform.ty()).isCloseTo(37.566499999999998, within(1e-12));
        assertThat(transform.lngScale()).isCloseTo(0.79264183480069705, within(1e-15));
    }

    @Test
    @DisplayName("apply는 등방 보정을 되돌려 진짜 위경도를 준다")
    void applyMatchesPython() {
        LatLng point = AffineFit.fitWgs84(WGS84_PAIRS).apply(25.0, 60.0);

        assertThat(point.lat()).isCloseTo(37.567062499999984, within(1e-11));
        assertThat(point.lng()).isCloseTo(126.9782405, within(1e-11));
    }

    @Test
    @DisplayName("등방 평면 피팅은 잉여 대응점에서도 최소자승 해를 준다")
    void fitAffineMatchesPython() {
        GeoTransform transform = AffineFit.fitAffine(List.of(
                new PointPair(0, 0, 10, 20),
                new PointPair(1, 0, 12, 23),
                new PointPair(0, 1, 9, 25),
                new PointPair(1, 1, 11, 28)));

        assertThat(transform.a()).isCloseTo(2.0, within(1e-12));
        assertThat(transform.b()).isCloseTo(-1.0, within(1e-12));
        assertThat(transform.c()).isCloseTo(3.0, within(1e-12));
        assertThat(transform.d()).isCloseTo(5.0, within(1e-12));
        assertThat(transform.tx()).isCloseTo(10.0, within(1e-12));
        assertThat(transform.ty()).isCloseTo(20.0, within(1e-12));
    }

    @Test
    @DisplayName("대응점이 3개 미만이거나 한 직선 위면 거절한다")
    void rejectsDegenerateInput() {
        assertThatThrownBy(() -> AffineFit.fitWgs84(List.of(new PointPair(0, 0, 0, 0), new PointPair(1, 1, 1, 1))))
                .isInstanceOf(IllegalArgumentException.class);

        // 세 점이 y=0 직선 위에 있으면 y 계수를 정할 수 없다.
        assertThatThrownBy(() -> AffineFit.fitAffine(List.of(
                        new PointPair(0, 0, 1, 1), new PointPair(1, 0, 2, 2), new PointPair(2, 0, 3, 3))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
