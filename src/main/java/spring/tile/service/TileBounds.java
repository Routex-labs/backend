package spring.tile.service;

/**
 * 슬리피맵 타일 하나가 덮는 WGS84 경계 상자.
 *
 * <p>경도는 타일 격자에 선형 비례하지만 위도는 Web Mercator라 역쌍곡선을 거친다.
 */
public record TileBounds(double west, double south, double east, double north) {

    /** 경계를 포함해 겹치는지. */
    public boolean intersects(double otherWest, double otherSouth, double otherEast, double otherNorth) {
        return west <= otherEast && otherWest <= east && south <= otherNorth && otherSouth <= north;
    }

    /** {west, south, east, north} 배열과의 교차. */
    public boolean intersects(double[] box) {
        return intersects(box[0], box[1], box[2], box[3]);
    }

    /** 점 하나가 이 타일 안인지. 점은 bbox 교차가 곧 포함 여부다. */
    public boolean containsPoint(double lng, double lat) {
        return intersects(lng, lat, lng, lat);
    }

    /**
     * 표준 슬리피맵(z/x/y, Web Mercator) 좌표를 경계 상자로.
     *
     * @throws IllegalArgumentException z가 음수이거나 x·y가 격자를 벗어나면
     */
    public static TileBounds of(int z, int x, int y) {
        if (z < 0 || z > 30) {
            throw new IllegalArgumentException("타일 좌표 범위를 벗어났습니다: z=%d, x=%d, y=%d".formatted(z, x, y));
        }
        long tilesPerAxis = 1L << z;
        if (x < 0 || x >= tilesPerAxis || y < 0 || y >= tilesPerAxis) {
            throw new IllegalArgumentException("타일 좌표 범위를 벗어났습니다: z=%d, x=%d, y=%d".formatted(z, x, y));
        }

        double axis = (double) tilesPerAxis;
        double west = x / axis * 360.0 - 180.0;
        double east = (x + 1) / axis * 360.0 - 180.0;
        // y는 위에서 아래로 세므로 y가 작을수록 북쪽이다.
        double north = edgeLatitude(y, axis);
        double south = edgeLatitude(y + 1, axis);
        return new TileBounds(west, south, east, north);
    }

    private static double edgeLatitude(long y, double tilesPerAxis) {
        double n = Math.PI - 2.0 * Math.PI * y / tilesPerAxis;
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }
}
