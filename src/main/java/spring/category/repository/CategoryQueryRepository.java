package spring.category.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import spring.place.domain.Store;

/**
 * 카테고리 화면 전용 투영 질의.
 *
 * <p>엔티티를 통째로 읽지 않고 컬럼만 고르는 이유: 자동완성 색인은 좌표를 한 번도 쓰지 않는데
 * {@code select Store}로 읽으면 폴리곤 JSON까지 꺼내 파싱한 뒤 버리게 된다. 응답에 안 나가도
 * 조회 비용은 그대로 든다.
 */
public interface CategoryQueryRepository extends Repository<Store, String> {

    /**
     * (층·대분류·소분류)별 매장 수.
     *
     * <p>대분류가 없는 매장은 pill을 만들 수 없어 클라이언트가 어차피 버린다 — 여기서 걸러
     * 응답을 더 줄인다. GROUP BY로 접으면 응답이 조합 수(실데이터 150행 안팎)로 고정된다.
     */
    @Query(
            """
            select f.name, s.category, s.subcategory, count(s.id)
              from Store s join s.floor f
             where f.building.id = :buildingId and s.category is not null
             group by f.name, s.category, s.subcategory
             order by f.name, s.category, s.subcategory
            """)
    List<Object[]> countByFloorAndCategory(@Param("buildingId") String buildingId);

    /**
     * 자동완성 색인 원본.
     *
     * <p>정렬을 명시하는 이유: 미지정이면 DB 반환 순서에 의존하게 되고, 같은 데이터에 호출마다
     * 순서가 달라지면 클라이언트가 색인을 캐시할 때 "내용은 같은데 다른 응답"으로 보인다.
     * 층 내림차순은 건물 요약의 층 순서(엘리베이터 버튼판)와 같고, id는 PK라 층 안에서 동점이
     * 없다 — 두 키로 전순서가 정해진다.
     */
    @Query(
            """
            select s.id, s.name, f.id, f.name, s.category, s.subcategory, s.entranceNodeId
              from Store s join s.floor f
             where f.building.id = :buildingId
             order by f.level desc, s.id
            """)
    List<Object[]> findStoreIndex(@Param("buildingId") String buildingId);
}
