package spring.route.dto;

import java.util.List;

/** 한 층의 길찾기 그래프 전체. */
public record FloorGraphResponse(
        GraphFloorResponse floor, List<GraphNodeResponse> nodes, List<GraphEdgeResponse> edges) {}
