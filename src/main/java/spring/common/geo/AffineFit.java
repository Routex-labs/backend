package spring.common.geo;

import java.util.List;

/**
 * 대응점들로 6-DOF affine 변환을 최소자승 피팅한다.
 *
 * <p>파이썬은 numpy {@code lstsq}(SVD)를 쓴다. 자바에는 대응 라이브러리가 없어 3×3
 * 정규방정식을 직접 푼다 — 미지수가 3개뿐이라 라이브러리를 들일 값이 없다. 정규방정식은
 * 조건수가 제곱되는 약점이 있어 <b>좌표를 평균으로 중심화한 뒤</b> 푼다. local_m이 수백 m
 * 단위라 중심화 없이는 유효숫자를 4~5자리 잃는다.
 *
 * <p>파이썬 구현과 같은 값을 내는지는 {@code AffineFitTest}가 대조한다.
 */
public final class AffineFit {

    private AffineFit() {}

    /**
     * (u, v)가 진짜 등방 평면 좌표일 때만 쓴다. (u, v)가 (lng, lat)이면 {@link #fitWgs84}를
     * 써야 한다 — 위도 1도와 경도 1도의 실제 거리가 다르다.
     *
     * @throws IllegalArgumentException 대응점이 3개 미만이면
     */
    public static GeoTransform fitAffine(List<PointPair> pairs) {
        if (pairs.size() < 3) {
            throw new IllegalArgumentException("affine 변환을 피팅하려면 대응점이 3개 이상 필요합니다.");
        }
        double meanX = pairs.stream().mapToDouble(PointPair::x).average().orElseThrow();
        double meanY = pairs.stream().mapToDouble(PointPair::y).average().orElseThrow();

        double[] uCoefficients = solveCentered(pairs, meanX, meanY, true);
        double[] vCoefficients = solveCentered(pairs, meanX, meanY, false);

        // 중심화해서 푼 절편을 원래 좌표계의 평행이동으로 되돌린다.
        double tx = uCoefficients[2] - uCoefficients[0] * meanX - uCoefficients[1] * meanY;
        double ty = vCoefficients[2] - vCoefficients[0] * meanX - vCoefficients[1] * meanY;

        return new GeoTransform(uCoefficients[0], uCoefficients[1], vCoefficients[0], vCoefficients[1], tx, ty);
    }

    /**
     * (x, y) → (lng, lat) 대응점으로 피팅한다. {@code pairs}의 u에 경도, v에 위도를 도 단위로
     * 그대로 넣는다.
     *
     * <p>위도/경도 1도의 거리가 다른 문제를 내부에서 보정한다 — 평균 위도로
     * {@code lngScale = cos(위도)}를 구해 경도에 곱해 등방 공간으로 만든 뒤 피팅하고, 그 값을
     * 결과에 실어 보낸다. {@link GeoTransform#apply}가 이를 이용해 진짜 위경도로 되돌린다.
     */
    public static GeoTransform fitWgs84(List<PointPair> pairs) {
        if (pairs.size() < 3) {
            throw new IllegalArgumentException("affine 변환을 피팅하려면 대응점이 3개 이상 필요합니다.");
        }
        double meanLat = pairs.stream().mapToDouble(PointPair::v).average().orElseThrow();
        double lngScale = Math.cos(Math.toRadians(meanLat));

        List<PointPair> isotropic =
                pairs.stream().map(p -> new PointPair(p.x(), p.y(), p.u() * lngScale, p.v())).toList();
        GeoTransform fitted = fitAffine(isotropic);

        return new GeoTransform(
                fitted.a(), fitted.b(), fitted.c(), fitted.d(), fitted.tx(), fitted.ty(), lngScale);
    }

    /** 중심화한 좌표로 [계수x, 계수y, 절편]을 푼다. {@code forU}가 false면 v를 맞춘다. */
    private static double[] solveCentered(List<PointPair> pairs, double meanX, double meanY, boolean forU) {
        // 정규방정식 (AᵀA)·β = Aᵀb 를 세운다. A의 열은 [x-x̄, y-ȳ, 1].
        double[][] normal = new double[3][4];
        for (PointPair pair : pairs) {
            double[] row = {pair.x() - meanX, pair.y() - meanY, 1.0};
            double target = forU ? pair.u() : pair.v();
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    normal[i][j] += row[i] * row[j];
                }
                normal[i][3] += row[i] * target;
            }
        }
        return solve3x3(normal);
    }

    /** 부분 피벗팅 가우스 소거. 입력은 3×4 확대행렬이고 내용을 덮어쓴다. */
    private static double[] solve3x3(double[][] augmented) {
        for (int column = 0; column < 3; column++) {
            int pivot = column;
            for (int row = column + 1; row < 3; row++) {
                if (Math.abs(augmented[row][column]) > Math.abs(augmented[pivot][column])) {
                    pivot = row;
                }
            }
            double[] swap = augmented[column];
            augmented[column] = augmented[pivot];
            augmented[pivot] = swap;

            if (augmented[column][column] == 0.0) {
                // 대응점이 한 직선 위에 있거나 모두 같은 점이면 여기로 온다.
                throw new IllegalArgumentException("대응점이 한 직선 위에 있어 affine 변환을 정할 수 없습니다.");
            }
            for (int row = column + 1; row < 3; row++) {
                double factor = augmented[row][column] / augmented[column][column];
                for (int col = column; col < 4; col++) {
                    augmented[row][col] -= factor * augmented[column][col];
                }
            }
        }
        double[] result = new double[3];
        for (int row = 2; row >= 0; row--) {
            double sum = augmented[row][3];
            for (int col = row + 1; col < 3; col++) {
                sum -= augmented[row][col] * result[col];
            }
            result[row] = sum / augmented[row][row];
        }
        return result;
    }
}
