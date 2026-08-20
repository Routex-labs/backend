package spring.building.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import spring.building.dto.BuildingDetailResponse;
import spring.building.dto.BuildingSummaryResponse;
import spring.building.service.BuildingService;

/**
 * 건물 목록·상세 HTTP 엔드포인트.
 *
 * <pre>
 *   GET /buildings          → 건물 목록(외곽선 같은 무거운 값 제외)
 *   GET /buildings/{id}     → 건물 상세
 * </pre>
 *
 * <p>경로 파라미터와 404 변환만 담당한다. 조회는 BuildingService가 한다.
 */
@RestController
@RequestMapping("/buildings")
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingService buildingService;

    @GetMapping
    public List<BuildingSummaryResponse> listBuildings() {
        return buildingService.list();
    }

    @GetMapping("/{buildingId}")
    public BuildingDetailResponse getBuilding(@PathVariable String buildingId) {
        return buildingService
                .detail(buildingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Building not found"));
    }
}
