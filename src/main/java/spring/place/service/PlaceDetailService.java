package spring.place.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.common.geometry.LocalPoint;
import spring.common.text.PlaceNames;
import spring.place.domain.Store;
import spring.place.dto.PlaceActionResponse;
import spring.place.dto.PlaceDetailResponse;
import spring.place.dto.PlaceLocationResponse;
import spring.place.dto.ProvenanceResponse;
import spring.place.repository.StoreRepository;

/**
 * 매장·시설 상세.
 *
 * <p>표시용 값은 DB에 없다 — 코어는 Store/Floor에서, 나머지는 오버레이 JSON에서 온다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceDetailService {

    private final StoreRepository storeRepository;
    private final PlaceOverlays placeOverlays;

    /** 건물·매장이 없으면 빈 Optional. */
    public Optional<PlaceDetailResponse> detail(String buildingId, String placeId) {
        return storeRepository
                .findById(placeId)
                // 다른 건물의 매장 id로 조회하면 없는 것으로 취급한다 — 건물별 URL인데 남의
                // 건물 매장이 열리면 층 라벨·길찾기가 전부 어긋난다.
                .filter(store -> store.getFloor() != null
                        && store.getFloor().getBuilding().getId().equals(buildingId))
                .map(store -> toDetail(buildingId, store));
    }

    private PlaceDetailResponse toDetail(String buildingId, Store store) {
        Map<String, Object> overlay = placeOverlays.forPlace(store.getId());
        String kind = PlaceKind.of(store.getSubcategory());

        return new PlaceDetailResponse(
                kind,
                store.getId(),
                PlaceNames.display(store.getName()),
                subtitle(store),
                OverlayReader.text(overlay, "logo"),
                store.getCategory(),
                store.getSubcategory(),
                new PlaceLocationResponse(
                        buildingId,
                        store.getFloor().getName(),
                        new LocalPoint(store.getCentroidXM(), store.getCentroidYM()),
                        store.getEntranceNodeId()),
                actions(store, kind),
                PlaceSectionBuilder.build(store, kind, overlay),
                new ProvenanceResponse(
                        // source는 출처의 **종류**다. 오버레이가 있으면 사람이 적은 것이므로
                        // manual이고, 그 문구를 어디서 옮겨 왔는지는 url이 따로 들고 간다.
                        overlay.isEmpty() ? "studio" : "manual",
                        OverlayReader.text(overlay, "updated_at"),
                        OverlayReader.text(overlay, "source")));
    }

    /** "B2 · 카페·베이커리" 형태. 소분류가 매장명과 같거나 비면 층만 남긴다. */
    static String subtitle(Store store) {
        String floorLabel = store.getFloor() == null ? null : store.getFloor().getName();
        String detail = store.getSubcategory() != null ? store.getSubcategory() : store.getCategory();
        if (detail != null && !detail.equals(PlaceNames.display(store.getName()))) {
            return floorLabel != null ? floorLabel + " · " + detail : detail;
        }
        return floorLabel != null ? floorLabel : "";
    }

    static List<PlaceActionResponse> actions(Store store, String kind) {
        List<PlaceActionResponse> actions = new ArrayList<>();
        // 입구 노드가 없으면 온디바이스 다익스트라가 도착점을 잡을 수 없다. 버튼을 띄워 두고
        // 눌렀을 때 실패하는 것보다 아예 내리지 않는 편이 낫다.
        if (store.getEntranceNodeId() != null) {
            actions.add(new PlaceActionResponse("directions", "길찾기"));
        }
        if (!PlaceKind.EXCLUDED.equals(kind)) {
            actions.add(new PlaceActionResponse("favorite", "저장"));
        }
        return List.copyOf(actions);
    }
}
