package spring.category.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.category.dto.CategoryCountResponse;
import spring.category.dto.StoreIndexResponse;
import spring.category.repository.CategoryQueryRepository;
import spring.common.geo.BuildingGeoTransforms;
import spring.common.geo.GeoTransform;
import spring.common.text.PlaceNames;
import spring.floormap.dto.StoreResponse;
import spring.floormap.service.PlaceMapper;
import spring.place.repository.StoreRepository;
import spring.place.service.PlaceKind;

/** 카테고리 pill·매장 검색·자동완성 색인. 셋 다 건물 스코프의 매장 목록 파생이다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryQueryRepository categoryQueryRepository;
    private final StoreRepository storeRepository;
    private final BuildingGeoTransforms geoTransforms;

    public List<CategoryCountResponse> categoryCounts(String buildingId) {
        return categoryQueryRepository.countByFloorAndCategory(buildingId).stream()
                .map(row -> new CategoryCountResponse(
                        (String) row[0], (String) row[1], (String) row[2], (Long) row[3]))
                .toList();
    }

    /**
     * 이름 부분 일치 검색. {@code query}가 비면 건물의 전체 매장이 나온다.
     *
     * <p>공유 폴리곤 분할을 걸지 않는다 — 이 목록은 이름으로 거른 부분 집합이라 짝을 못 찾아
     * 아무것도 나뉘지 않는다. 분할이 필요한 화면은 층 지도 응답을 쓴다.
     */
    public List<StoreResponse> searchStores(String buildingId, String query) {
        GeoTransform transform = geoTransforms.forBuilding(buildingId);
        return storeRepository.searchByBuildingIdAndName(buildingId, query).stream()
                .map(store -> PlaceMapper.toStore(store, transform, Map.of()))
                .toList();
    }

    public List<StoreIndexResponse> storeIndex(String buildingId) {
        return categoryQueryRepository.findStoreIndex(buildingId).stream()
                .map(row -> new StoreIndexResponse(
                        (String) row[0],
                        PlaceNames.display((String) row[1]),
                        (String) row[2],
                        (String) row[3],
                        (String) row[4],
                        (String) row[5],
                        // 상세와 같은 규칙으로 판정한다. 클라이언트가 소분류 목록을 들고 스스로
                        // 판정하면 그 목록이 서버와 갈라지는 날 조용히 어긋난다.
                        PlaceKind.of((String) row[5]),
                        (String) row[6]))
                .toList();
    }
}
