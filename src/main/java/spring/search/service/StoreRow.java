package spring.search.service;

import java.util.List;
import java.util.Map;

/**
 * 질의 매칭이 보는 매장 한 줄.
 *
 * <p>엔티티를 통째로 읽지 않는다. 매칭은 이름·분류·facet만 보는데 {@code select Store}로 읽으면
 * 폴리곤 JSON까지 꺼내 파싱한 뒤 버린다 — 파이썬 실측으로 매장 1,640건에서 그 역직렬화가
 * 235초 중 180초였다(랭킹 자체는 5.7초). 그래서 여기도 컬럼만 고른다.
 */
public record StoreRow(
        String id,
        String name,
        String category,
        String subcategory,
        double centroidXM,
        double centroidYM,
        String entranceNodeId,
        Map<String, List<String>> searchFacets,
        String floorId,
        String floorName,
        int floorLevel) {

    /** {@code search_facets}를 방어적으로 읽는다. 미태깅 매장이 대다수라 빈 맵이 정상이다. */
    public List<String> facet(String axis) {
        if (searchFacets == null) {
            return List.of();
        }
        List<String> values = searchFacets.get(axis);
        return values == null ? List.of() : values;
    }
}
