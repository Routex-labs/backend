package spring.search.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import spring.place.domain.Store;
import spring.search.service.StoreRow;

/** 자연어 질의 전용 투영 질의. 컬럼만 고르는 이유는 {@link StoreRow} 주석에 있다. */
public interface QuerySearchRepository extends Repository<Store, String> {

    @Query(
            """
            select new spring.search.service.StoreRow(
                     s.id, s.name, s.category, s.subcategory,
                     s.centroidXM, s.centroidYM, s.entranceNodeId, s.searchFacets,
                     f.id, f.name, f.level)
              from Store s join s.floor f
             where f.building.id = :buildingId
            """)
    List<StoreRow> findAllByBuilding(@Param("buildingId") String buildingId);

    /**
     * 현재 층으로 좁힌 목록.
     *
     * <p>{@code floorKey}는 층 라벨("B2")과 내부 id("FL-...")를 모두 받는다. 클라이언트는 사용자가
     * 보는 라벨만 들고 있고, {@code (building_id, name)}이 유니크라 건물 안에서 라벨이 유일하다.
     */
    @Query(
            """
            select new spring.search.service.StoreRow(
                     s.id, s.name, s.category, s.subcategory,
                     s.centroidXM, s.centroidYM, s.entranceNodeId, s.searchFacets,
                     f.id, f.name, f.level)
              from Store s join s.floor f
             where f.building.id = :buildingId and (f.name = :floorKey or f.id = :floorKey)
            """)
    List<StoreRow> findByBuildingAndFloor(@Param("buildingId") String buildingId, @Param("floorKey") String floorKey);

    /** 형태소 사전용 전 매장명. 건물을 가리지 않는다 — 사전은 프로세스 전역이다. */
    @Query("select s.name from Store s")
    List<String> findAllNames();
}
