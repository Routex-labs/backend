package spring.floormap.dto;

import java.util.List;
import spring.common.geometry.LatLng;
import spring.common.geometry.LocalPoint;

/**
 * 매장 하나. 화장실·엘리베이터 같은 편의시설도 매장으로 내려간다.
 *
 * <p>{@code entranceWgs84}는 원본에서 <b>다비오 공식 POI 핀 위치</b>다. 한 폴리곤에 매장이
 * 여럿 붙은 자리에서 centroid는 전부 같은 값이라, 화면이 라벨을 흩을 때 쓸 수 있는 유일한
 * 좌표다.
 *
 * <p>wgs84 필드들은 좌표 변환이 불가능하면 null이다.
 */
public record StoreResponse(
        String id,
        String floorId,
        String name,
        String category,
        String subcategory,
        LocalPoint centroidLocalM,
        LatLng centroidWgs84,
        List<LatLng> polygonWgs84,
        LocalPoint entranceLocalM,
        LatLng entranceWgs84,
        String entranceNodeId,
        List<LocalPoint> polygonLocalM) {}
