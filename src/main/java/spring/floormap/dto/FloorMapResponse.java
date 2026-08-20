package spring.floormap.dto;

import java.util.List;
import spring.common.geometry.LatLng;
import spring.common.geometry.LocalPoint;
import spring.route.dto.FloorGraphResponse;

/**
 * 층 하나를 그리는 데 필요한 전체 묶음. Flutter 지도 화면이 이 응답 하나로 층을 렌더한다.
 *
 * <p>못 걷는 면(non_walkable)은 <b>여기 싣지 않는다.</b> 화면은 MVT 타일의 non_walkable
 * 레이어로 그리므로 넣어도 아무도 읽지 않고, B4는 기둥만 209개라 층을 열 때마다 폴리곤
 * 수백 개가 헛돈다.
 */
public record FloorMapResponse(
        FloorResponse floor,
        String navigationCoordinateSystem,
        String mapCalibrationVersion,
        List<LocalPoint> footprintLocalM,
        List<LatLng> footprintWgs84,
        FloorGraphResponse navigationGraph,
        List<StoreResponse> stores,
        List<PoiResponse> pois) {}
