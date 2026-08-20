package spring.place.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import spring.place.domain.Store;

public interface StoreRepository extends JpaRepository<Store, String> {

    List<Store> findByFloorId(String floorId);

    /** 상세는 층 라벨과 소속 건물을 함께 읽는다 — 지연로딩으로 두면 조회가 세 번 나간다. */
    @Override
    @EntityGraph(attributePaths = {"floor", "floor.building"})
    Optional<Store> findById(String id);

    /** 이름 부분 일치 검색. 빈 문자열이면 건물의 전체 매장이 나온다. */
    @Query("select s from Store s where s.floor.building.id = :buildingId and s.name like %:query%")
    List<Store> searchByBuildingIdAndName(@Param("buildingId") String buildingId, @Param("query") String query);
}
