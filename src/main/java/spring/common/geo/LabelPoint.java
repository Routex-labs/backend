package spring.common.geo;

import java.util.List;

/**
 * 폴리곤 안에서 <b>라벨을 놓기 가장 좋은 점</b>을 고른다(pole of inaccessibility).
 *
 * <p>왜 무게중심이 아닌가, 왜 격자 탐색인가, 왜 볼록 4각형은 건너뛰는가는
 * docs/decisions/007-라벨-점은-무게중심이-아니다.md.
 *
 * <p>좌표는 (lng, lat) 순서다 — 호출부가 wgs84 링을 넘긴다.
 */
public final class LabelPoint {

    private LabelPoint() {}

    /** 격자를 몇 단계 좁힐지. 단계마다 셀이 1/3이 되고 지름의 1/16에서 시작하므로 5단계면 약 1/4000이다. */
    private static final int REFINE_STEPS = 5;

    /** 첫 격자의 등분 수. 너무 성기면 좁은 통로형 매장에서 내부 점을 하나도 못 찍고 시작한다. */
    private static final int INITIAL_DIVISIONS = 16;

    /**
     * 후보가 현 최선을 대체하려면 넘어야 하는 <b>상대 여유</b>.
     *
     * <p>길쭉한 직사각형에서는 경계 최원점이 한 점이 아니라 긴 축 위 선분(능선) 전체로 동률인데,
     * 순수 {@code >} 비교는 부동소수점 반올림만으로도 능선을 타고 시드에서 멀어진다 — 실제 배포에서
     * 최대 25m 라벨 이탈로 나타났다. 절대값이 아니라 상대 여유인 이유는 입력이 경위도라 거리 규모가
     * 1e-5 deg까지 내려가기 때문이다.
     */
    private static final double IMPROVEMENT_MARGIN = 1e-9;

    /**
     * 폴리곤 링(닫히지 않아도 된다)의 면적 무게중심.
     *
     * <p>계산은 첫 꼭짓점 기준 <b>상대 좌표로 옮겨서</b> 한다. 무게중심은 평행이동에 불변이라
     * 수학적으로 같은 값인데, 절대 경위도(~127, ~37)로 계산하면 외적 항이 ~10³인데 진짜 면적은
     * ~10⁻⁹ deg²라 상쇄에서 살아남는 유효자리가 모자라 무게중심이 수 km 벗어난다(실측). 그러면
     * 아래 격자 탐색의 시드가 무력화되고, 시드를 잃은 탐색이 동률 능선 위 임의 점을 고른다.
     *
     * <p>면적이 0에 가까우면(선분처럼 납작한 폴리곤) 꼭짓점 평균으로 떨어진다.
     */
    public static double[] polygonCentroid(List<double[]> ring) {
        int count = ring.size();
        double originX = ring.get(0)[0];
        double originY = ring.get(0)[1];
        double doubledArea = 0.0;
        double cx = 0.0;
        double cy = 0.0;
        for (int index = 0; index < count; index++) {
            double x0 = ring.get(index)[0] - originX;
            double y0 = ring.get(index)[1] - originY;
            double x1 = ring.get((index + 1) % count)[0] - originX;
            double y1 = ring.get((index + 1) % count)[1] - originY;
            double cross = x0 * y1 - x1 * y0;
            doubledArea += cross;
            cx += (x0 + x1) * cross;
            cy += (y0 + y1) * cross;
        }
        if (Math.abs(doubledArea) < 1e-12) {
            double sumX = 0.0;
            double sumY = 0.0;
            for (double[] point : ring) {
                sumX += point[0];
                sumY += point[1];
            }
            return new double[] {sumX / count, sumY / count};
        }
        double area = doubledArea * 0.5;
        return new double[] {originX + cx / (6 * area), originY + cy / (6 * area)};
    }

    /**
     * 링 안에서 라벨을 놓을 점.
     *
     * <p>꼭짓점이 3개 미만이면 계산할 폴리곤이 아니므로 꼭짓점 평균으로, 탐색이 내부 점을 하나도
     * 못 찾으면 무게중심으로 떨어진다 — 라벨을 안 그리는 것보다 낫다.
     */
    public static double[] of(List<double[]> ring) {
        if (ring.size() < 3) {
            if (ring.isEmpty()) {
                return new double[] {0.0, 0.0};
            }
            double sumX = 0.0;
            double sumY = 0.0;
            for (double[] point : ring) {
                sumX += point[0];
                sumY += point[1];
            }
            return new double[] {sumX / ring.size(), sumY / ring.size()};
        }

        // 볼록 4각형 조기 탈출. 실데이터의 B3~B6는 주차구획형 4꼭짓점 사각형이 층당 150~220개라
        // 이 조합이 타일 생성 비용의 대부분이었다(무거운 층 한 패스가 1F의 2.4~2.7배).
        // 볼록 폴리곤의 무게중심은 항상 내부이고, 직사각형·평행사변형에서는 그것이 곧 경계 최원점이라
        // 격자 탐색과 결과가 같다.
        List<double[]> vertices = ring;
        if (vertices.size() > 1 && samePoint(vertices.get(0), vertices.get(vertices.size() - 1))) {
            vertices = vertices.subList(0, vertices.size() - 1);
        }
        if (vertices.size() == 4 && isConvexQuadrilateral(vertices)) {
            return polygonCentroid(vertices);
        }

        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double[] point : ring) {
            minX = Math.min(minX, point[0]);
            maxX = Math.max(maxX, point[0]);
            minY = Math.min(minY, point[1]);
            maxY = Math.max(maxY, point[1]);
        }
        double span = Math.max(maxX - minX, maxY - minY);
        if (span <= 0) {
            return polygonCentroid(ring);
        }

        // 무게중심이 내부면 그것을 출발점으로 삼는다. 직사각형처럼 이미 답인 폴리곤에서 탐색이
        // 그보다 나쁜 점을 고르는 일을 막는다.
        double[] best = polygonCentroid(ring);
        double bestDistance = contains(best, ring) ? distanceToBoundary(best, ring) : -1.0;

        double step = span / INITIAL_DIVISIONS;
        for (int refinement = 0; refinement < REFINE_STEPS; refinement++) {
            for (double y = minY; y <= maxY; y += step) {
                for (double x = minX; x <= maxX; x += step) {
                    double[] candidate = {x, y};
                    if (!contains(candidate, ring)) {
                        continue;
                    }
                    double distance = distanceToBoundary(candidate, ring);
                    // 시드가 밖이라 bestDistance가 -1.0이면 임계도 음수라 내부 후보가 항상 통과한다.
                    if (distance > bestDistance * (1.0 + IMPROVEMENT_MARGIN)) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
            // 다음 단계는 지금 최선 주변만 더 촘촘히 본다.
            minX = best[0] - step;
            maxX = best[0] + step;
            minY = best[1] - step;
            maxY = best[1] + step;
            step /= 3;
        }
        return best;
    }

    /** 네 모서리의 외적 부호가 전부 같으면 볼록. 외적 0(일직선·중복 꼭짓점)은 퇴화로 보고 제외한다. */
    private static boolean isConvexQuadrilateral(List<double[]> ring) {
        int sign = 0;
        for (int index = 0; index < 4; index++) {
            double[] p0 = ring.get(index);
            double[] p1 = ring.get((index + 1) % 4);
            double[] p2 = ring.get((index + 2) % 4);
            double cross = (p1[0] - p0[0]) * (p2[1] - p1[1]) - (p1[1] - p0[1]) * (p2[0] - p1[0]);
            if (cross == 0) {
                return false;
            }
            int current = cross > 0 ? 1 : -1;
            if (sign == 0) {
                sign = current;
            } else if (current != sign) {
                return false;
            }
        }
        return true;
    }

    /** 짝수-홀수 규칙. 경계 위 점의 판정은 정의하지 않는다 — 호출부가 거리로 다시 거른다. */
    private static boolean contains(double[] point, List<double[]> ring) {
        boolean inside = false;
        int count = ring.size();
        for (int index = 0; index < count; index++) {
            double[] p0 = ring.get(index);
            double[] p1 = ring.get((index + 1) % count);
            if ((p0[1] > point[1]) != (p1[1] > point[1])) {
                double crossingX = (p1[0] - p0[0]) * (point[1] - p0[1]) / (p1[1] - p0[1]) + p0[0];
                if (point[0] < crossingX) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    /** 점에서 링의 가장 가까운 변까지의 거리. */
    private static double distanceToBoundary(double[] point, List<double[]> ring) {
        double best = Double.MAX_VALUE;
        int count = ring.size();
        for (int index = 0; index < count; index++) {
            double[] p0 = ring.get(index);
            double[] p1 = ring.get((index + 1) % count);
            double dx = p1[0] - p0[0];
            double dy = p1[1] - p0[1];
            double lengthSquared = dx * dx + dy * dy;
            if (lengthSquared == 0) {
                best = Math.min(best, Math.hypot(point[0] - p0[0], point[1] - p0[1]));
                continue;
            }
            // 변 위로 정사영한 위치를 [0,1]로 자른다(끝점 밖이면 끝점이 최근접).
            double t = ((point[0] - p0[0]) * dx + (point[1] - p0[1]) * dy) / lengthSquared;
            t = Math.max(0.0, Math.min(1.0, t));
            best = Math.min(best, Math.hypot(point[0] - (p0[0] + t * dx), point[1] - (p0[1] + t * dy)));
        }
        return best;
    }

    private static boolean samePoint(double[] a, double[] b) {
        return a[0] == b[0] && a[1] == b[1];
    }
}
