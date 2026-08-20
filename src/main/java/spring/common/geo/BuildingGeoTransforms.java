package spring.common.geo;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import spring.common.geometry.LatLng;
import spring.common.geometry.LocalPoint;

/**
 * 건물의 local_m → WGS84 변환을 요청 시점에 피팅한다.
 *
 * <p>DB 컬럼으로 저장하지 않고 매번 즉석 피팅하는 이유: 타일 렌더링과 JSON 층 지도 응답이
 * 같은 함수를 공유해야 두 경로가 같은 좌표를 가리킨다. 저장해 두면 한쪽만 낡는다.
 *
 * <p>엔티티 대신 JdbcTemplate으로 읽는다 — 필요한 건 네 컬럼 투영뿐이라 JPA가 살 게 없고,
 * 그 덕에 이 클래스가 특정 기능 패키지의 엔티티에 묶이지 않는다.
 */
@Service
@RequiredArgsConstructor
public class BuildingGeoTransforms {

    /**
     * 실측 앵커가 전혀 없는 합성 데이터셋을 임의로 배치할 기준점(서울시청). 클라이언트의 GPS
     * 실패 폴백 위치와 맞춰서, 데모에서 우연히라도 같은 동네에 보이게 한다. 실측이 아니라
     * "지도에 뭔가 보이게" 하려는 자리끼움이다.
     */
    private static final double SYNTHETIC_ANCHOR_LAT = 37.5665;

    private static final double SYNTHETIC_ANCHOR_LNG = 126.9780;
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    private static final String ANCHOR_SQL =
            """
            select n.x_m, n.y_m, n.lat, n.lng
              from nodes n
              join floors f on n.floor_id = f.id
             where f.building_id = ?
               and n.lat is not null
               and n.lng is not null
            """;

    private final JdbcTemplate jdbcTemplate;

    /** 실측 앵커가 3개 미만이면 합성 대응점으로 대체하므로 <b>절대 null을 돌려주지 않는다.</b> */
    public GeoTransform forBuilding(String buildingId) {
        List<PointPair> pairs = jdbcTemplate.query(
                ANCHOR_SQL,
                (rs, rowNum) -> new PointPair(rs.getDouble("x_m"), rs.getDouble("y_m"), rs.getDouble("lng"), rs.getDouble("lat")),
                buildingId);

        return AffineFit.fitWgs84(pairs.size() >= 3 ? pairs : syntheticPairs());
    }

    /** local_m 점 목록을 위경도로 옮긴다. 비어 있으면 빈 목록. */
    public static List<LatLng> toLatLng(List<LocalPoint> points, GeoTransform transform) {
        List<LatLng> converted = new ArrayList<>(points.size());
        for (LocalPoint point : points) {
            converted.add(transform.apply(point.x(), point.y()));
        }
        return converted;
    }

    /** local_m 1m = 실좌표 1m로 매핑하는 가상 대응점 3개(원점과 두 축 방향) — 피팅 최소 개수다. */
    private static List<PointPair> syntheticPairs() {
        double lngScale = Math.cos(Math.toRadians(SYNTHETIC_ANCHOR_LAT));
        double[][] samples = {{0.0, 0.0}, {100.0, 0.0}, {0.0, 100.0}};

        List<PointPair> pairs = new ArrayList<>(samples.length);
        for (double[] sample : samples) {
            double lat = SYNTHETIC_ANCHOR_LAT + sample[1] / METERS_PER_DEGREE_LAT;
            double lng = SYNTHETIC_ANCHOR_LNG + sample[0] / (METERS_PER_DEGREE_LAT * lngScale);
            pairs.add(new PointPair(sample[0], sample[1], lng, lat));
        }
        return pairs;
    }
}
