package spring.route.dto;

import java.util.List;

/**
 * 건물 전체 길찾기 그래프. 전 층 노드 + 층 내부 간선 + 수직 전이 간선을 한데 담아
 * 클라이언트가 층 간 경로까지 온디바이스 다익스트라로 계산하게 한다.
 *
 * <p>{@code revision}은 이 그래프 내용의 체크섬이다. 클라이언트·타일 캐시가 "그래프가
 * 그대로인가"를 값 하나로 판단하는 무효화 키다. 계산 방식은 {@code GraphRevision} 참고.
 */
public record BuildingGraphResponse(
        GraphBuildingResponse building,
        String vertical,
        String revision,
        List<GraphFloorResponse> floors,
        List<GraphNodeResponse> nodes,
        List<GraphEdgeResponse> edges) {}
