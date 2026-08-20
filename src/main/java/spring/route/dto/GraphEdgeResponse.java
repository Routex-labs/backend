package spring.route.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import spring.common.geometry.LocalPoint;

/**
 * 두 노드를 잇는 간선.
 *
 * <p>내부 이름은 fromNodeId/toNodeId지만 API 키는 짧은 {@code from}/{@code to}다 —
 * {@code @JsonProperty}가 네이밍 전략보다 우선한다.
 *
 * <p><b>수직 전이의 도착 지점은 {@code to}와 {@code toFloorId}가 단일 원천이다.</b>
 * 클라이언트는 이름·그룹으로 도착 노드를 다시 고르지 않는다.
 */
public record GraphEdgeResponse(
        String id,
        @JsonProperty("from") String fromNodeId,
        @JsonProperty("to") String toNodeId,
        double lengthM,
        double costM,
        boolean bidirectional,
        List<LocalPoint> geometryLocalM,
        String transferMode,
        String fromFloorId,
        String toFloorId) {}
