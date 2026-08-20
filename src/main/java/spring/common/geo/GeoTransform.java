package spring.common.geo;

import spring.common.geometry.LatLng;

/**
 * (x, y) → (u, v) 2D affine 변환(6-DOF).
 *
 * <pre>
 *   u = a*x + b*y + tx
 *   v = c*x + d*y + ty
 * </pre>
 *
 * <p>왜 6-DOF인가: 설계와 실측 근거는 docs/decisions/004-좌표변환은-6DOF-affine.md.
 */
public record GeoTransform(double a, double b, double c, double d, double tx, double ty, double lngScale) {

    /** wgs84가 아닌 평면 쌍(예: local_m → SVG px)은 경도 보정이 필요 없어 lngScale=1이다. */
    public GeoTransform(double a, double b, double c, double d, double tx, double ty) {
        this(a, b, c, d, tx, ty, 1.0);
    }

    /** 입력 좌표 하나를 (u, v)로 옮긴다. lngScale 보정은 하지 않는다. */
    public double[] applyUv(double x, double y) {
        return new double[] {a * x + b * y + tx, c * x + d * y + ty};
    }

    /**
     * local_m 좌표 하나를 위경도로 옮긴다.
     *
     * <p>{@code applyUv}가 돌려주는 u는 등방 공간의 "경도류"라 lngScale로 나눠야 진짜 경도가
     * 된다({@link AffineFit#fitWgs84} 참고).
     */
    public LatLng apply(double xM, double yM) {
        double[] uv = applyUv(xM, yM);
        return new LatLng(uv[0] / lngScale, uv[1]);
    }
}
