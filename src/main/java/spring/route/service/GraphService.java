package spring.route.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.building.domain.Building;
import spring.building.domain.Floor;
import spring.building.repository.BuildingRepository;
import spring.building.repository.FloorRepository;
import spring.common.geometry.LocalPoint;
import spring.route.domain.Edge;
import spring.route.domain.Node;
import spring.route.dto.BuildingGraphResponse;
import spring.route.dto.FloorGraphResponse;
import spring.route.dto.GraphBuildingResponse;
import spring.route.dto.GraphEdgeResponse;
import spring.route.dto.GraphFloorResponse;
import spring.route.dto.GraphNodeResponse;
import spring.route.repository.EdgeRepository;
import spring.route.repository.NodeRepository;

/**
 * 길찾기 그래프 조회.
 *
 * <p>최단 경로는 계산하지 않는다 — 클라이언트가 이 그래프로 온디바이스 다익스트라를 돌린다
 * (docs/decisions/002-경로계산은-클라이언트에-남긴다.md).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GraphService {

    /**
     * 수직 이동 정책. 층 간 전이 간선 중 무엇을 그래프에 실을지 고른다.
     *
     * <ul>
     *   <li>{@code auto} — 둘 다(비용 모델이 층수에 따라 자동으로 고른다)
     *   <li>{@code elevator} — 엘리베이터만(에스컬레이터 회피)
     *   <li>{@code escalator} — 에스컬레이터만
     * </ul>
     */
    public static final Set<String> VERTICAL_POLICIES = Set.of("auto", "elevator", "escalator");

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;

    /** 없는 건물·층이면 빈 Optional. */
    public Optional<FloorGraphResponse> floorGraph(String buildingId, String floorName) {
        return floorRepository.findByBuildingIdAndName(buildingId, floorName).map(this::toFloorGraph);
    }

    /** 층 지도 응답이 그래프 레이어를 끼울 때도 이 메서드를 쓴다 — 두 경로가 같은 값을 내야 한다. */
    public FloorGraphResponse toFloorGraph(Floor floor) {
        List<GraphNodeResponse> nodes = nodeRepository.findByFloorId(floor.getId()).stream()
                .map(node -> toNode(node, null))
                .toList();
        List<GraphEdgeResponse> edges = edgeRepository.findByFloorId(floor.getId()).stream()
                .map(edge -> toEdge(edge, null))
                .toList();

        return new FloorGraphResponse(new GraphFloorResponse(floor.getId(), floor.getName()), nodes, edges);
    }

    /**
     * 건물 전체 그래프. 층별 그래프와 달리 수직 전이 간선까지 포함한다 — 전이 간선은
     * floor가 null이라 층별 조회에서 빠지므로 여기서만 합류한다.
     *
     * @param vertical {@link #VERTICAL_POLICIES} 중 하나. 검증은 컨트롤러가 한다.
     */
    public Optional<BuildingGraphResponse> buildingGraph(String buildingId, String vertical) {
        Optional<Building> found = buildingRepository.findById(buildingId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Building building = found.get();

        List<Floor> floors = floorRepository.findByBuildingId(buildingId);
        List<Node> nodes = nodeRepository.findByBuildingId(buildingId);
        Set<String> nodeIds = nodes.stream().map(Node::getId).collect(Collectors.toSet());

        List<Edge> edges = new ArrayList<>(edgeRepository.findIntraFloorEdgesByBuildingId(buildingId));
        if (!nodeIds.isEmpty()) {
            edgeRepository.findTransferEdges(nodeIds).stream()
                    .filter(edge -> verticalAllows(vertical, edge.getTransferMode()))
                    .forEach(edges::add);
        }

        // 전이 간선의 양 끝 층은 간선이 아니라 노드에만 있다. 이미 읽어 둔 nodes를 재사용해
        // 간선마다 노드를 다시 조회하지 않는다.
        Map<String, String> nodeFloorIds =
                nodes.stream().collect(Collectors.toMap(Node::getId, node -> node.getFloor().getId()));

        List<GraphNodeResponse> nodeDtos =
                nodes.stream().map(node -> toNode(node, node.getFloor().getId())).toList();
        List<GraphEdgeResponse> edgeDtos =
                edges.stream().map(edge -> toEdge(edge, nodeFloorIds)).toList();

        return Optional.of(new BuildingGraphResponse(
                new GraphBuildingResponse(building.getId(), building.getName()),
                vertical,
                GraphRevision.of(nodeDtos, edgeDtos),
                floors.stream()
                        .map(floor -> new GraphFloorResponse(floor.getId(), floor.getName()))
                        .toList(),
                nodeDtos,
                edgeDtos));
    }

    /** 층 내부 간선(transferMode=null)은 정책과 무관하게 항상 포함한다. */
    static boolean verticalAllows(String policy, String transferMode) {
        if (transferMode == null) {
            return true;
        }
        return switch (policy) {
            case "elevator", "escalator" -> policy.equals(transferMode);
            default -> true;
        };
    }

    private static GraphNodeResponse toNode(Node node, String floorId) {
        return new GraphNodeResponse(
                node.getId(), node.getType(), node.getName(), node.getXM(), node.getYM(), node.getLat(), node.getLng(), floorId);
    }

    /**
     * @param nodeFloorIds 노드 id → 소속 층 id. null이면(단일 층 그래프) 간선 자신의 층을 쓴다.
     */
    private static GraphEdgeResponse toEdge(Edge edge, Map<String, String> nodeFloorIds) {
        String fromNodeId = edge.getFromNode().getId();
        String toNodeId = edge.getToNode().getId();

        String fromFloorId;
        String toFloorId;
        if (nodeFloorIds == null) {
            String edgeFloorId = edge.getFloor() == null ? null : edge.getFloor().getId();
            fromFloorId = edgeFloorId;
            toFloorId = edgeFloorId;
        } else {
            fromFloorId = nodeFloorIds.get(fromNodeId);
            toFloorId = nodeFloorIds.get(toNodeId);
        }

        List<LocalPoint> geometry = edge.getGeometry() == null ? List.of() : edge.getGeometry();

        return new GraphEdgeResponse(
                edge.getId(),
                fromNodeId,
                toNodeId,
                edge.getLengthM(),
                edge.getCostM(),
                edge.isBidirectional(),
                geometry,
                edge.getTransferMode(),
                fromFloorId,
                toFloorId);
    }
}
