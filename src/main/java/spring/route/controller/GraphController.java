package spring.route.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import spring.route.dto.BuildingGraphResponse;
import spring.route.dto.FloorGraphResponse;
import spring.route.service.GraphService;

/**
 * 길찾기 그래프 HTTP 엔드포인트.
 *
 * <pre>
 *   GET /buildings/{id}/graph?vertical=auto        → 건물 전체(수직 전이 포함)
 *   GET /buildings/{id}/floors/{floor}/graph       → 한 층
 * </pre>
 */
@RestController
@RequestMapping("/buildings")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    @GetMapping("/{buildingId}/graph")
    public BuildingGraphResponse getBuildingGraph(
            @PathVariable String buildingId, @RequestParam(defaultValue = "auto") String vertical) {
        // RFC 9110이 422를 UNPROCESSABLE_CONTENT로 바꿔 불렀다. 코드값은 그대로 422다.
        // 잘못된 정책은 조용히 auto로 떨어뜨리지 않는다 — 클라이언트 오타가 "엘리베이터만"
        // 요청했는데 에스컬레이터 경로를 받는 상황이 되기 때문이다.
        if (!GraphService.VERTICAL_POLICIES.contains(vertical)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT, "vertical must be one of " + GraphService.VERTICAL_POLICIES);
        }
        return graphService
                .buildingGraph(buildingId, vertical)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Building not found"));
    }

    @GetMapping("/{buildingId}/floors/{floorName}/graph")
    public FloorGraphResponse getFloorGraph(@PathVariable String buildingId, @PathVariable String floorName) {
        return graphService
                .floorGraph(buildingId, floorName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Floor not found"));
    }
}
