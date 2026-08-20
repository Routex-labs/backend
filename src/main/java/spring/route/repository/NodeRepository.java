package spring.route.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import spring.route.domain.Node;

public interface NodeRepository extends JpaRepository<Node, String> {

    List<Node> findByFloorId(String floorId);

    @Query("select n from Node n where n.floor.building.id = :buildingId")
    List<Node> findByBuildingId(@Param("buildingId") String buildingId);
}
