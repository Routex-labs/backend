package spring.search.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import spring.search.dto.AiRequest;
import spring.search.dto.DestinationResponse;
import spring.search.dto.DiscoveryResponse;
import spring.search.dto.InfoResponse;
import spring.search.dto.QueryRequest;
import spring.search.service.QueryRanking;
import spring.search.service.QuerySearchService;

/**
 * 자연어 질의 엔드포인트.
 *
 * <p>본문 검증과 404 변환만 한다. 매칭은 {@link QuerySearchService}가 한다.
 *
 * <p><b>검증 실패는 400이 아니라 422다.</b> 클라이언트가 404와 422를 함께 "결과 없음"으로 처리하고
 * 그 밖의 상태는 예외로 던지기 때문에(http_destination_repository.dart), 400을 내면 검색 시트가
 * 통째로 죽는다. 파이썬(FastAPI)이 본문 검증 실패에 422를 내던 것을 그대로 맞춘다.
 */
@RestController
@RequestMapping("/query")
@RequiredArgsConstructor
public class QueryController {

    private final QuerySearchService querySearchService;

    @PostMapping("/destination")
    public DestinationResponse destination(@RequestBody QueryRequest request) {
        String text = validated(request.text());
        return querySearchService
                .matchDestination(request.buildingId(), text, request.currentFloorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Building not found"));
    }

    @PostMapping("/ai")
    public DiscoveryResponse ai(@RequestBody AiRequest request) {
        String text = validated(request.text());
        return querySearchService
                .discover(request.buildingId(), text, request.currentFloorId(), request.selectedFacets(), Boolean.TRUE.equals(request.showAll()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Building not found"));
    }

    @PostMapping("/info")
    public InfoResponse info(@RequestBody QueryRequest request) {
        String text = validated(request.text());
        return querySearchService
                .matchInfo(request.buildingId(), text, request.currentFloorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Building not found"));
    }

    /** 공백만 있는 질의는 빈 문자열과 같이 막되 원문 자체는 보존한다. */
    private static String validated(String text) {
        if (text == null || text.isBlank() || text.length() > QueryRanking.MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "text must contain non-whitespace characters");
        }
        return text;
    }
}
