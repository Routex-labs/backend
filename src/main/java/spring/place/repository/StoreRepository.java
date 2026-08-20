package spring.place.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import spring.place.domain.Store;

public interface StoreRepository extends JpaRepository<Store, String> {

    List<Store> findByFloorId(String floorId);

    /** 이름 부분 일치 검색. 빈 문자열이면 건물의 전체 매장이 나온다. */
    @Query("select s from Store s where s.floor.building.id = :buildingId and s.name like %:query%")
    List<Store> searchByBuildingIdAndName(@Param("buildingId") String buildingId, @Param("query") String query);
}
