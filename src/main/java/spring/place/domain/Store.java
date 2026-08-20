package spring.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import spring.building.domain.Floor;
import spring.common.geometry.LocalPoint;

/**
 * 매장. 화장실·엘리베이터 같은 편의시설도 여기 저장된다 — "매장"은 원천 데이터의 분류지
 * 상업 시설만 뜻하지 않는다.
 *
 * <p>여러 기능(floormap·category·place·tile)이 이 엔티티를 함께 읽는다. 가장 강하게
 * 연관된 기능인 place에 두고 나머지가 import한다.
 */
@Entity
@Table(
        name = "stores",
        indexes = {@Index(name = "idx_stores_floor", columnList = "floor_id"), @Index(name = "idx_stores_name", columnList = "name")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Store {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    /** 매장명. 원문 그대로 둔다 — 구두점(A.P.C.)을 서버에서 지우지 않는다. */
    @Column(nullable = false)
    private String name;

    private String category;

    private String subcategory;

    // Hibernate는 끝에 붙은 대문자 앞에 언더바를 넣지 않아 centroidXM이 centroid_xm이 된다.
    @Column(name = "centroid_x_m", nullable = false)
    private double centroidXM;

    @Column(name = "centroid_y_m", nullable = false)
    private double centroidYM;

    /**
     * 입구 좌표. 원본에서 이 점은 <b>다비오 공식 POI 핀 위치</b>라, 한 폴리곤에 매장이 여럿
     * 붙은 자리에서 매장을 서로 구분하는 유일한 좌표다 — centroid는 그 매장들이 전부 같다.
     */
    @Column(name = "entrance_x_m")
    private Double entranceXM;

    @Column(name = "entrance_y_m")
    private Double entranceYM;

    /** 입구와 이어진 그래프 노드 id. 온디바이스 경로의 도착 노드이며, 없으면 길찾기가 안 된다. */
    private String entranceNodeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<LocalPoint> polygon;

    /** 검색 전용 추천 태그(intents/styles/...). 대부분 매장은 비어 있다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, List<String>> searchFacets;
}
