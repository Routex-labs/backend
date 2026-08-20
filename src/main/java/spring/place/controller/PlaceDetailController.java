package spring.place.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import spring.building.repository.BuildingRepository;
import spring.place.dto.PlaceDetailResponse;
import spring.place.service.PlaceDetailService;

/**
 * 매장·시설 상세.
 *
 * <pre>GET /buildings/{id}/places/{placeId}</pre>
 *
 * <p>층 지도 응답에 끼워 넣지 않고 탭한 순간 1건만 가져오는 이유: 한 층에 매장이 최대 240건이라
 * 상세를 층 응답에 합치면 지도 첫 렌더가 그만큼 느려진다. 층 응답은 지도를 그리는 최소 묶음으로
 * 유지한다.
 */
@RestController
@RequestMapping("/buildings")
@RequiredArgsConstructor
public class PlaceDetailController {

    private final PlaceDetailService placeDetailService;
    private final BuildingRepository buildingRepository;

    @GetMapping("/{buildingId}/places/{placeId}")
    public PlaceDetailResponse getPlaceDetail(@PathVariable String buildingId, @PathVariable String placeId) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Building not found");
        }
        return placeDetailService
                .detail(buildingId, placeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found"));
    }
}
