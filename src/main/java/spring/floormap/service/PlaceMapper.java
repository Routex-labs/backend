package spring.floormap.service;

import java.util.List;
import java.util.Map;
import spring.common.geo.BuildingGeoTransforms;
import spring.common.geo.GeoTransform;
import spring.common.geometry.LatLng;
import spring.common.geometry.LocalPoint;
import spring.floormap.dto.PoiResponse;
import spring.floormap.dto.StoreResponse;
import spring.place.domain.Poi;
import spring.place.domain.Store;

/**
 * 매장·POI 엔티티 → 지도 응답 변환. 매장 검색(category 패키지)도 같은 변환을 쓴다.
 *
 * <p>{@code sharedPolygons}는 한 폴리곤을 둘이 나눠 쓰는 자리의 자기 칸이다. 없으면 원본
 * 폴리곤을 쓴다.
 */
public final class PlaceMapper {

    private PlaceMapper() {}

    public static StoreResponse toStore(Store store, GeoTransform transform, Map<String, List<LocalPoint>> sharedPolygons) {
        List<LocalPoint> polygon = sharedPolygons.getOrDefault(store.getId(), store.getPolygon());

        LatLng centroidWgs84 = transform.apply(store.getCentroidXM(), store.getCentroidYM());
        LatLng entranceWgs84 = null;
        LocalPoint entranceLocalM = optionalPoint(store.getEntranceXM(), store.getEntranceYM(), store.getId());
        if (entranceLocalM != null) {
            entranceWgs84 = transform.apply(entranceLocalM.x(), entranceLocalM.y());
        }
        List<LatLng> polygonWgs84 =
                polygon == null || polygon.isEmpty() ? null : BuildingGeoTransforms.toLatLng(polygon, transform);

        return new StoreResponse(
                store.getId(),
                store.getFloor().getId(),
                store.getName(),
                store.getCategory(),
                store.getSubcategory(),
                new LocalPoint(store.getCentroidXM(), store.getCentroidYM()),
                centroidWgs84,
                polygonWgs84,
                entranceLocalM,
                entranceWgs84,
                store.getEntranceNodeId(),
                polygon);
    }

    public static PoiResponse toPoi(Poi poi, GeoTransform transform) {
        return new PoiResponse(
                poi.getId(),
                poi.getType(),
                poi.getName(),
                new LocalPoint(poi.getXM(), poi.getYM()),
                transform.apply(poi.getXM(), poi.getYM()),
                poi.getLinkedNodeId());
    }

    /** 선택 좌표는 x·y가 모두 없을 때만 "없음"이다. 한쪽만 있으면 데이터가 깨진 것이라 오류다. */
    private static LocalPoint optionalPoint(Double x, Double y, String storeId) {
        if (x == null && y == null) {
            return null;
        }
        if (x == null || y == null) {
            throw new IllegalStateException("매장 %s 입구 좌표 값이 불완전합니다.".formatted(storeId));
        }
        return new LocalPoint(x, y);
    }
}
