package spring.building.dto;

import java.util.List;
import spring.common.geometry.LatLng;
import spring.common.geometry.LocalPoint;

/**
 * 건물 상세. 목록 응답에 도면을 그리는 데 필요한 값을 더한다.
 *
 * <p>레코드는 상속이 없어 요약 필드를 그대로 다시 적는다. JSON은 어차피 평평해야 하므로
 * 상속으로 얻을 게 없다.
 *
 * <p>계약: docs/api/contract.md
 */
public record BuildingDetailResponse(
        String id,
        String name,
        List<String> floors,
        String defaultFloor,
        Double areaM2,
        Double perimeterM,
        List<LocalPoint> footprintLocalM,
        /** 야외 지도용 wgs84 외곽선. 아핀 변환 이식 전이라 항상 null이다. */
        List<LatLng> footprintWgs84,
        /** 타일 URL의 {@code ?v=} 토큰. tile 패키지 이식 전이라 항상 null이다. */
        String tileRevision) {}
