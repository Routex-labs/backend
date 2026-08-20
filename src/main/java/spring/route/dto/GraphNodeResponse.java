package spring.route.dto;

/**
 * 경로 그래프의 정점.
 *
 * <p>{@code floorId}는 건물 전체 그래프에서만 채워진다 — 층별 그래프는 단일 층이라 생략한다.
 * 전 층 노드가 한 그래프에 섞일 때 클라이언트가 층별로 다시 나누는 근거다.
 */
public record GraphNodeResponse(
        String id, String type, String name, double xM, double yM, Double lat, Double lng, String floorId) {}
