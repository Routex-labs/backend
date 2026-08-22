package spring.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import spring.event.dto.BuildingEventsResponse;
import spring.event.service.BuildingEventCatalog;

/**
 * 건물 행사(팝업·다이닝·쇼핑) HTTP 엔드포인트.
 *
 * <pre>
 *   GET /buildings/{id}/events → 그 건물의 행사 한 벌(쪽 + 행사)
 * </pre>
 *
 * <p><b>날짜 파라미터가 없다.</b> "오늘 열리는 것"을 서버가 고르면 그 응답은 만들어진 순간부터
 * 틀리기 시작한다 — 서버는 기간이라는 규칙을 주고 화면이 자기 로컬 날짜와 견준다. 근거는
 * {@link BuildingEventsResponse}.
 */
@RestController
@RequestMapping("/buildings")
@RequiredArgsConstructor
public class BuildingEventController {

    private final BuildingEventCatalog catalog;

    @GetMapping("/{buildingId}/events")
    public BuildingEventsResponse getEvents(@PathVariable String buildingId) {
        return catalog.forBuilding(buildingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Events not found"));
    }
}
