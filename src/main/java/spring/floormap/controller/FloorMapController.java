package spring.floormap.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import spring.floormap.dto.FloorMapResponse;
import spring.floormap.service.FloorMapService;

/**
 * 층 지도 HTTP 엔드포인트.
 *
 * <pre>GET /buildings/{id}/floors/{floor} → 층 지도 데이터(매장 + POI + 그래프)</pre>
 */
@RestController
@RequestMapping("/buildings")
@RequiredArgsConstructor
public class FloorMapController {

    private final FloorMapService floorMapService;

    @GetMapping("/{buildingId}/floors/{floorName}")
    public FloorMapResponse getFloorMap(@PathVariable String buildingId, @PathVariable String floorName) {
        return floorMapService
                .floorMap(buildingId, floorName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Floor not found"));
    }
}
