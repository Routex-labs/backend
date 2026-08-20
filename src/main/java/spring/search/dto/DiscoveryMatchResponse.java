package spring.search.dto;

import java.util.List;
import java.util.Map;
import spring.common.geometry.LatLng;
import spring.common.geometry.LocalPoint;

/**
 * 추천 매장 1건. 목적지 계약에 추천 근거만 덧붙인다.
 *
 * <p>레코드는 상속이 없어 {@link QueryMatchResponse}의 필드를 그대로 다시 적었다 — JSON은 어차피
 * 평평해야 한다.
 *
 * @param matchedFacets 이번 질문·선택과 실제로 겹친 태그만. 원본 facet 전체를 보내지 않는다
 * @param reason matchedFacets에서만 조립한 추천 이유. 근거 태그가 없으면 null — 추측하지 않는다
 */
public record DiscoveryMatchResponse(
        String storeId,
        String name,
        String category,
        String subcategory,
        String floorId,
        String floorName,
        String entranceNodeId,
        LocalPoint centroidLocalM,
        LatLng centroidWgs84,
        Map<String, List<String>> matchedFacets,
        String reason) {}
