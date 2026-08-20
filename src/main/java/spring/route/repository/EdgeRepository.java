package spring.route.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import spring.route.domain.Edge;

public interface EdgeRepository extends JpaRepository<Edge, String> {

    List<Edge> findByFloorId(String floorId);

    /** 층 내부 간선. 전이 간선은 floor가 null이라 여기서 빠진다. */
    @Query("select e from Edge e where e.floor.building.id = :buildingId")
    List<Edge> findIntraFloorEdgesByBuildingId(@Param("buildingId") String buildingId);

    /**
     * 이 건물 노드를 잇는 수직 전이 간선.
     *
     * <p><b>양 끝 노드가 모두</b> 이 건물 소속인 것만 고른다. from만 확인하면 to가 다른 건물
     * 노드인 간선이 딸려 와, 그래프에 없는 노드를 가리키는 dangling 간선이 응답에 실린다.
     */
    @Query("select e from Edge e where e.floor is null and e.fromNode.id in :nodeIds and e.toNode.id in :nodeIds")
    List<Edge> findTransferEdges(@Param("nodeIds") Collection<String> nodeIds);
}
