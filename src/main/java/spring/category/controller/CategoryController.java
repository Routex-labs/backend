package spring.category.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import spring.building.repository.BuildingRepository;
import spring.category.dto.CategoryCountResponse;
import spring.category.dto.StoreIndexResponse;
import spring.category.service.CategoryService;
import spring.floormap.dto.StoreResponse;

/**
 * 카테고리 칩·매장 검색·자동완성 색인.
 *
 * <pre>
 *   GET /buildings/{id}/categories    → (층·대분류·소분류)별 매장 수
 *   GET /buildings/{id}/stores?q=     → 매장 이름 검색(q 없으면 전체)
 *   GET /buildings/{id}/store-index   → 자동완성용 경량 목록(좌표 없음)
 * </pre>
 *
 * <p>{@code store-index}를 {@code /stores?light=1}로 얹지 않고 별도 경로로 둔 이유: 같은 경로가
 * 요청에 따라 다른 스키마를 돌려주면 응답 타입을 하나로 고정할 수 없고, 층 지도를 그리는
 * 소비자와 검색 색인 소비자가 같은 계약을 공유하게 된다.
 */
@RestController
@RequestMapping("/buildings")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final BuildingRepository buildingRepository;

    @GetMapping("/{buildingId}/categories")
    public List<CategoryCountResponse> listCategories(@PathVariable String buildingId) {
        requireBuilding(buildingId);
        return categoryService.categoryCounts(buildingId);
    }

    @GetMapping("/{buildingId}/stores")
    public List<StoreResponse> searchStores(
            @PathVariable String buildingId, @RequestParam(defaultValue = "") String q) {
        requireBuilding(buildingId);
        return categoryService.searchStores(buildingId, q);
    }

    @GetMapping("/{buildingId}/store-index")
    public List<StoreIndexResponse> listStoreIndex(@PathVariable String buildingId) {
        requireBuilding(buildingId);
        return categoryService.storeIndex(buildingId);
    }

    /** 없는 건물은 빈 배열이 아니라 404다 — 오타 난 건물 id가 "매장 0곳"으로 보이면 안 된다. */
    private void requireBuilding(String buildingId) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Building not found");
        }
    }
}
