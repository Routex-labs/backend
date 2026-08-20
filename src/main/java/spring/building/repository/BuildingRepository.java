package spring.building.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import spring.building.domain.Building;

/**
 * 건물 조회.
 *
 * <p>두 메서드 모두 {@code @EntityGraph}로 층을 함께 읽는다. 응답이 언제나 층 목록을
 * 포함하므로 지연로딩으로 두면 건물 수만큼 추가 쿼리가 나간다(N+1).
 */
public interface BuildingRepository extends JpaRepository<Building, String> {

    @Override
    @EntityGraph(attributePaths = "floors")
    List<Building> findAll();

    @Override
    @EntityGraph(attributePaths = "floors")
    Optional<Building> findById(String id);
}
