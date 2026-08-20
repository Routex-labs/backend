package spring.building.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import spring.building.domain.Floor;

/** 층 조회. 건물 안에서 층 라벨(B2, 1F)은 유일하다 — DB 유니크 제약이 보장한다. */
public interface FloorRepository extends JpaRepository<Floor, String> {

    Optional<Floor> findByBuildingIdAndName(String buildingId, String name);

    List<Floor> findByBuildingId(String buildingId);
}
